package core.wb

import chisel3._
import conf.Conf.{prfN, prfW, wbWidth}

class PhyRegFile extends Module {
  import PhyRegFile._

  val read = IO(Vec(wbWidth * 2, Flipped(new ReadIO)))
  val write = IO(Vec(wbWidth, Flipped(new WriteIO)))

  val prf = Reg(Vec(prfN, UInt(32.W)))

  read.foreach(r => {
    r.src := Mux(r.rs === 0.U, 0.U, prf(r.rs))
  })

  write.foreach(w => {
    when (w.en && w.rd =/= 0.U) {
      prf(w.rd) := w.data
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
  class ReadIO extends Bundle {
    val rs = Output(UInt(prfW.W))
    val src = Input(UInt(32.W))
  }

  class WriteIO extends Bundle {
    val en = Output(Bool())
    val rd = Output(UInt(prfW.W))
    val data = Output(UInt(32.W))
  }
}
