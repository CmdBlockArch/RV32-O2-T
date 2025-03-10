package core.fetch.cache

import chisel3._
import utils._

import conf.Conf.ICache._

class CacheMeta extends Module {
  import CacheMeta._

  val read = IO(Flipped(new ReadIO))
  val write = IO(Flipped(new WriteIO))

  val valid = RegInit(VecInit(Seq.fill(setN)(
    VecInit(Seq.fill(wayN)(false.B))
  )))
  val tag = Module(new SRAM(setN, Vec(wayN, UInt(tagW.W))))

  assert(!(read.en && write.en)) // 读写互斥

  tag.io.en := read.en || write.en
  tag.io.we := write.en
  tag.io.addr := Mux(write.en, write.index, read.index)
  tag.io.din := write.data.map(_.tag)

  when (write.en) {
    (0 until wayN).foreach(i => {
      valid(write.index)(i) := write.data(i).valid
    })
  }

  read.data := (0 until wayN).map(i => {
    val wayData = Wire(new MetaBundle)
    wayData.valid := valid(read.index)(i)
    wayData.tag := tag.io.dout(i)
    wayData
  })

}

object CacheMeta {
  class MetaBundle extends Bundle {
    val valid = Bool()
    val tag = UInt(tagW.W)
  }

  class ReadIO extends Bundle {
    val en = Output(Bool())
    val index = Output(UInt(indexW.W))
    val data = Input(Vec(wayN, new MetaBundle))
  }

  class WriteIO extends Bundle {
    val en = Output(Bool())
    val index = Output(UInt(indexW.W))
    val data = Output(Vec(wayN, new MetaBundle))
  }
}
