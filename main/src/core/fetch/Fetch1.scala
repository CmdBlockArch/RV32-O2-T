package core.fetch

import chisel3._
import chisel3.util._
import utils._

import Config.ICache._
import cache.CacheMeta

class Fetch1 extends Module {
  import Fetch1._

  val in = IO(Flipped(new Fetch0.OutBundle))
  val io = IO(new Bundle {
    val ready = Output(Bool())
    val flush = Input(Bool())
  })

  val valid = RegInit(false.B)
  val cur = Reg(new Fetch0.OutBundle)
  when (io.flush) {
    valid := false.B
  } .otherwise {
    valid := true.B
    cur := in
  }
  io.ready := !valid || cur.hit

}

object Fetch1 {
  class outBundle extends Bundle {
    val pc = PC()
  }
}
