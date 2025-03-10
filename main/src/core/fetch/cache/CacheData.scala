package core.fetch.cache

import chisel3._
import utils._

import Config.ICache._

class CacheData extends Module {
  import CacheData._

  val read = IO(Flipped(new ReadIO))
  val write = IO(Flipped(new WriteIO))

  assert(!(read.en && write.en)) // 读写互斥
  val index = Mux(write.en, write.index, read.index)
  val way = Mux(write.en, write.way, read.way)

  val data = Seq.fill(wayN)(Module(new SRAM(setN, dataType)))
  read.data := DontCare
  for (i <- 0 until wayN) {
    data(i).io.en := (read.en || write.en) && way === i.U
    data(i).io.we := (write.en && write.way === i.U)
    data(i).io.addr := index
    data(i).io.din := write.data

    when (way === i.U) {
      read.data := data(i).io.dout
    }
  }
}

object CacheData {
  val dataType = Vec(blockN, UInt(32.W))

  class ReadIO extends Bundle {
    val en = Output(Bool())
    val index = Output(UInt(indexW.W))
    val way = Output(UInt(wayW.W))
    val data = Input(Vec(blockN, UInt(32.W)))
  }

  class WriteIO extends Bundle {
    val en = Output(Bool())
    val index = Output(UInt(indexW.W))
    val way = Output(UInt(wayW.W))
    val data = Output(Vec(blockN, UInt(32.W)))
  }
}
