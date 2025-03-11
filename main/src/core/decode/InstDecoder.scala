package core.decode

import chisel3._
import chisel3.util._

class InstDecoder extends Module {
  import InstDecoder._

  val inst = IO(Input(UInt(32.W)))
  val res = IO(Output(new DecodeBundle))

  res.rs1 := inst(19, 15)
  res.rs2 := inst(24, 20)
  res.rd := inst(11, 7)
}

object InstDecoder {
  class DecodeBundle extends Bundle {
    val rs1 = UInt(5.W)
    val rs2 = UInt(5.W)
    val rd = UInt(5.W)
  }
}
