package core.fetch.cache

import chisel3._
import utils._

import Config.ICache._

class CacheMeta extends Module {
  import CacheMeta._

  val readIO = IO(Flipped(new ReadIO))
  val writeIO = IO(Flipped(new WriteIO))

  val valid = RegInit(VecInit(Seq.fill(setN)(
    VecInit(Seq.fill(wayN)(false.B))
  )))
  val tag = Module(new SRAM(setN, Vec(wayN, UInt(tagW.W))))

  assert(!(readIO.en && writeIO.en)) // 读写互斥

  tag.io.en := readIO.en || writeIO.en
  tag.io.we := writeIO.en
  tag.io.addr := Mux(writeIO.en, writeIO.index, readIO.index)
  tag.io.din := writeIO.data.map(_.tag)

  when (writeIO.en) {
    (0 until wayN).foreach(i => {
      valid(writeIO.index)(i) := writeIO.data(i).valid
    })
  }

  readIO.data := (0 until wayN).map(i => {
    val wayData = Wire(new MetaType)
    wayData.valid := valid(readIO.index)(i)
    wayData.tag := tag.io.dout(i)
    wayData
  })

}

object CacheMeta {
  class MetaType extends Bundle {
    val valid = Bool()
    val tag = UInt(tagW.W)
  }

  class ReadIO extends Bundle {
    val en = Output(Bool())
    val index = Output(UInt(indexW.W))
    val data = Input(Vec(wayN, new MetaType))
  }

  class WriteIO extends Bundle {
    val en = Output(Bool())
    val index = Output(UInt(indexW.W))
    val data = Output(Vec(wayN, new MetaType))
  }
}
