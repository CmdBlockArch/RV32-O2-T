package core.decode

import chisel3._
import conf.Conf.{decodeWidth, fetchWidth}
import core.fetch.Fetch
import utils._

class Decode extends PiplineModule(new Fetch.OutBundle, new Decode.OutBundle) {
  assert(fetchWidth == decodeWidth)
  res.pc := cur.pc
  res.valid := cur.valid

  val decoder = Seq.fill(decodeWidth)(Module(new InstDecoder))
  for (i <- 0 until decodeWidth) {
    decoder(i).inst := cur.inst(i)
    res.gpr(i) := decoder(i).gpr
    res.inst(i) := decoder(i).res
  }
}

object Decode {
  class OutBundle extends Bundle {
    val pc = PC()
    val valid = Vec(decodeWidth, Bool())
    val gpr = Vec(decodeWidth, new InstDecoder.GprBundle)
    val inst = Vec(decodeWidth, new InstDecoder.DecodeBundle)
  }
}
