package utils

import chisel3._
import chisel3.util._

import conf.Conf.resetVec

class PC extends Bundle {
  val h = UInt(30.W)
  def full = Cat(h, 0.U(2.W))
  def next(i: Int) = (h + i.U).asTypeOf(PC())
}

object PC {
  def apply(): PC = new PC
  val resetPC = resetVec(31, 2).asTypeOf(PC())

  def regInit = {
    RegInit(PC(), resetPC)
  }
}
