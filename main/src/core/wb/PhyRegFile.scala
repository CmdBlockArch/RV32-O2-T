package core.wb

import chisel3._
import conf.Conf.{prfN, prfW, wbWidth}

class PhyRegFile extends Module {
  import PhyRegFile._

  val probe = IO(Vec(2, Flipped(new ProbeIO)))
  val read = IO(Vec(wbWidth * 2, Flipped(new ReadIO)))
  val write = IO(Vec(wbWidth, Flipped(new WriteIO)))

  val prf = Reg(Vec(prfN, UInt(32.W)))
  val ready = RegInit(VecInit(Seq.fill(prfN)(false.B)))

  probe.foreach(p => {
    p.ready := Mux(p.rs === 0.U, true.B, ready(p.rs))
  })

  read.foreach(r => {
    r.src := Mux(r.rs === 0.U, 0.U, prf(r.rs))
    when (r.en && r.rs =/= 0.U) {
      // TODO: 实现背靠背执行时，需要把en信号去掉。这里的assert只是为了验证
      assert(ready(r.rs))
    }
  })

  write.foreach(w => {
    when (w.en && w.rd =/= 0.U) {
      prf(w.rd) := w.data
      ready(w.rd) := true.B
    }
  })

  // 寄存器写入冲突检测
  for (i <- 0 until wbWidth) {
    for (j <- 0 until wbWidth) {
      if (i != j) {
        val wConflict = write(i).en && write(j).en && write(i).rd === write(j).rd
        assert(!wConflict)
      }
    }
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
