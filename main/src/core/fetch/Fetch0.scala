package core.fetch

import chisel3._
import chisel3.util._
import utils._

import Config.ICache._
import cache.CacheMeta

class Fetch0 extends Module {
  import Fetch0._

  val out = IO(new OutIO)
  val metaRead = IO(new CacheMeta.ReadIO)
  val io = IO(new Bundle {
    val redirect = Input(Bool())
    val redirectPC = Input(PC())

    val ready = Input(Bool())
  })

  val pc = PC.regInit
  when (io.redirect) {
    pc := io.redirectPC
  }

  metaRead.en := io.ready && !io.redirect
  metaRead.index := getIndex(pc)
  out.pc := pc

  val tag = getTag(pc)
  val hitVec = VecInit(metaRead.data.map(d => d.valid && d.tag === tag))
  out.hit := hitVec.reduce(_ || _)
  out.hitWay := Mux1H(hitVec, (0 until wayN).map(_.U))
}

object Fetch0 {
  class OutIO extends Bundle {
    val pc = Output(PC())

    val hit = Bool()
    val hitWay = UInt(wayW.W)
  }
}
