package core.fetch

import chisel3._
import chisel3.util._

import utils._
import cache.CacheData
import conf.Conf.fetchWidth
import conf.Conf.ICache._

class Fetch1 extends Module {
  import Fetch1._

  val in = IO(Flipped(new Fetch0.OutBundle))
  val out = IO(new Fetch.OutBundle)
  val dataRead = IO(new CacheData.ReadIO)
  val io = IO(new Bundle {
    val ready = Output(Bool())
    val flush = Input(Bool())
  })

  val valid = RegInit(false.B)
  val cur = Reg(new Fetch0.OutBundle)
  val req = RegInit(false.B)
  when (io.flush) {
    valid := false.B
  } .otherwise {
    valid := true.B
    cur := in
  }
  io.ready := !valid || cur.hit

  val index = getIndex(cur.pc)
  val offset = getOffset(cur.pc)

  dataRead.en := valid && cur.hit
  dataRead.index := getIndex(cur.pc)
  dataRead.way := cur.hitWay

  assert(fetchWidth == 2)
  out.pc := cur.pc
  out.inst(0).valid := valid && cur.hit
  out.inst(0).inst := dataRead.data(offset)
  out.inst(1).valid := valid && cur.hit && offset =/= (blockN - 1).U
  out.inst(1).inst := dataRead.data(offset + 1.U)
}

object Fetch1 {

}
