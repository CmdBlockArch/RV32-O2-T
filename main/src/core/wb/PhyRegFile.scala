package core.wb

import chisel3._
import conf.Conf.{commitWidth, debug, dispatchWidth, prfN, prfW, wbWidth}
import utils.Debug._

class PhyRegFile extends Module {
  import PhyRegFile._

  val probe = IO(Vec(dispatchWidth * 2, Flipped(new ProbeIO)))
  val wbRd = IO(Output(Vec(wbWidth, UInt(prfW.W))))
  val read = IO(Vec(wbWidth * 2, Flipped(new ReadIO)))
  val write = IO(Vec(wbWidth, Flipped(new WriteIO)))
  val prfFree = IO(Input(Vec(commitWidth, UInt(prfW.W))))
  val io = IO(new Bundle {
    val flush = Input(Bool())
    val commitRat = Input(Vec(32, UInt(prfW.W)))
  })

  val prf = Reg(Vec(prfN, UInt(32.W)))
  val ready = RegInit(VecInit(Seq.fill(prfN)(false.B)))

  // 寄存器就绪状态探测
  probe.foreach(p => {
    p.ready := Mux(p.rs === 0.U, true.B, ready(p.rs))
  })

  // 唤醒信号产生
  for (i <- 0 until wbWidth) {
    wbRd(i) := Mux(write(i).en, write(i).rd, 0.U)
  }

  // 寄存器堆读端口
  read.foreach(r => {
    r.src := Mux(r.rs === 0.U, 0.U, prf(r.rs))
    when (r.en && r.rs =/= 0.U) {
      // TODO: 实现背靠背执行时，需要把en信号去掉。这里的assert只是为了验证
      assert(ready(r.rs))
    }
  })

  // 寄存器堆写端口
  write.foreach(w => {
    when (w.en && w.rd =/= 0.U) {
      prf(w.rd) := w.data
      ready(w.rd) := true.B
    }
  })

  // 寄存器释放，取消就绪状态
  prfFree.foreach(r => {
    ready(r) := false.B
  })

  // 冲刷流水线，重置就绪状态
  when (io.flush) {
    ready := 0.U.asTypeOf(ready)
    io.commitRat.foreach(r => {
      when (r.orR) {
        ready(r) := true.B
      }
    })
  }

  // 寄存器写入冲突检测
  for (i <- 0 until wbWidth) {
    for (j <- 0 until wbWidth) {
      if (i != j) {
        val wConflict = write(i).en && write(i).rd.orR &&
                        write(j).en && write(j).rd.orR &&
                        write(i).rd === write(j).rd
        assert(!wConflict)
      }
    }
  }

  // debug
  val dbgOut = DebugIO(new Bundle {
    val prf = Output(Vec(prfN, UInt(32.W)))
  })
  if (debug) {
    dbgOut.get.prf := prf
  }
}

object PhyRegFile {
  class ProbeIO extends Bundle {
    val rs = Output(UInt(prfW.W))
    val ready = Input(Bool())
  }

  class ReadIO extends Bundle {
    val en = Output(Bool())
    val rs = Output(UInt(prfW.W))
    val src = Input(UInt(32.W))
  }

  class WriteIO extends Bundle {
    val en = Output(Bool())
    val rd = Output(UInt(prfW.W))
    val data = Output(UInt(32.W))
  }
}
