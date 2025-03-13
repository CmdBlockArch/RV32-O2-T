package core.fetch.cache

import chisel3._
import utils._

import conf.Conf.ICache._

class CacheMeta extends Module {
  import CacheMeta._

  val read = IO(Flipped(new ReadIO))
  val write = IO(Flipped(new WriteIO))
  val flush = IO(Input(Bool()))

  assert(!(read.en && write.en)) // 读写互斥

  val valid = RegInit(VecInit(Seq.fill(setN)(
    VecInit(Seq.fill(wayN)(false.B))
  )))
  val data = Seq.fill(wayN)(Module(new SRAM(setN, new MetaBundle)))

  read.valid := valid(read.index)
  for (i <- 0 until wayN) {
    data(i).io.en := read.en || (write.en && write.way === i.U)
    data(i).io.we := write.en && write.way === i.U
    data(i).io.addr := Mux(write.en, write.index, read.index)
    data(i).io.din := write.data

    read.data(i) := data(i).io.dout
  }

  when (write.en) {
    valid(write.index)(write.way) := true.B
  }

  when (flush) {
    valid := 0.U.asTypeOf(valid)
  }
}

object CacheMeta {
  class MetaBundle extends Bundle {
    val tag = UInt(tagW.W)
  }

  class ReadIO extends Bundle {
    val en = Output(Bool())
    val index = Output(UInt(indexW.W))
    val valid = Input(Vec(wayN, Bool()))
    val data = Input(Vec(wayN, new MetaBundle))
  }

  class WriteIO extends Bundle {
    val en = Output(Bool())
    val index = Output(UInt(indexW.W))
    val way = Output(UInt(wayW.W))
    val data = Output(new MetaBundle)
  }
}
