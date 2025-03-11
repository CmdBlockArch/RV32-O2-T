package core.fetch

import chisel3._
import chisel3.util._

import utils._
import cache.CacheMeta
import conf.Conf.fetchWidth
import conf.Conf.ICache._

class Fetch0 extends Module {
  import Fetch0._

  val out = IO(new OutBundle)
  val metaRead = IO(new CacheMeta.ReadIO)
  val io = IO(new Bundle {
    val redirect = Input(Bool())
    val redirectPC = Input(PC())

    val ready = Input(Bool())
  })

  val pc = PC.regInit
  when (io.redirect) {
    pc := io.redirectPC
  } .elsewhen (io.ready) {
    pc := pc.next(fetchWidth)
  }

  metaRead.en := io.ready && !io.redirect
  metaRead.index := getIndex(pc)
  out.pc := pc

  val tag = getTag(pc)
  val hitVec = VecInit((0 until wayN).map(i => {
    metaRead.valid(i) && metaRead.data(i).tag === tag
  }))
  out.hit := hitVec.reduce(_ || _)
  out.hitWay := Mux1H(hitVec, (0 until wayN).map(_.U))
}

object Fetch0 {
  class OutBundle extends Bundle {
    val pc = PC()
    val hit = Bool()
    val hitWay = UInt(wayW.W)
  }
}
