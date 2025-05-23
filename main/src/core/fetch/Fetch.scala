package core.fetch

import chisel3._
import chisel3.util._
import conf.Conf.fetchWidth
import core.fetch.cache.{CacheData, CacheMeta}
import perip.AxiReadArb
import utils._

class Fetch extends Module {
  val out = IO(Decoupled(new Fetch.OutBundle))
  val arbRead = IO(new AxiReadArb.ReadIO)
  val io = IO(new Bundle {
    val redirect = Input(Bool())
    val redirectPC = Input(PC())

    val fenceI = Input(Bool())
  })

  val fetch0 = Module(new Fetch0)
  val fetch1 = Module(new Fetch1)
  val cacheMeta = Module(new CacheMeta)
  val cacheData = Module(new CacheData)

  fetch1.in := fetch0.out
  cacheMeta.read :<>= fetch0.metaRead
  fetch0.io.redirect := io.redirect
  fetch0.io.redirectPC := io.redirectPC

  out :<>= fetch1.out
  cacheData.read :<>= fetch1.dataRead
  cacheMeta.write :<>= fetch1.metaWrite
  cacheData.write :<>= fetch1.dataWrite
  arbRead :<>= fetch1.arbRead
  fetch0.io.ready := fetch1.io.ready
  fetch1.io.flush := io.redirect

  cacheMeta.flush := io.fenceI
}

object Fetch {
  class OutBundle extends Bundle {
    val pc = PC()
    val valid = Vec(fetchWidth, Bool())
    val inst = Vec(fetchWidth, UInt(32.W))
  }
}
