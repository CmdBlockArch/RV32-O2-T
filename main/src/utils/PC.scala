package utils

import chisel3._
import chisel3.util._

class PC extends Bundle {
  val h = UInt(30.W)
  def full = Cat(h, 0.U(2.W))
  def next(i: Int) = h + i.U
}

object PC {
  val resetPC = Config.resetVec(31, 2).asTypeOf(new PC)

  def apply(): PC = new PC
  def regInit = {
    RegInit(PC(), resetPC)
  }
}
