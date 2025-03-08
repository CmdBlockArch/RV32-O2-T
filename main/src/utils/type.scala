package utils

import chisel3._
import chisel3.util._

class PC extends Bundle {
  val r = UInt(30.W)
  def full = Cat(r, 0.U(2.W))
  def next(i: Int) = r + i.U
}

object PC {
  def apply() = new PC
}

object Addr {
  def apply() = UInt(32.W)
}

object Word {
  def apply() = UInt(32.W)
}
