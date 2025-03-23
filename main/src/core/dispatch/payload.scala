package core.dispatch

import chisel3._
import conf.Conf.{prfW, robW}
import utils._

trait ExuBundle extends Bundle {
  val robIdx = UInt(robW.W)
  val rd = UInt(prfW.W)
}

class AluBundle extends ExuBundle {
  val imm = UInt(32.W)
  val useImm = Bool()

  val func = UInt(3.W)
  val sign = Bool()
}

class BruBundle extends ExuBundle {
  val imm = UInt(32.W)
  val pc = PC()
  val func = UInt(3.W)
  val rdSel = UInt(2.W)
  val jalr = Bool()
}

class MduBundle extends ExuBundle {
  val func = UInt(3.W)
}

class LsuBundle extends ExuBundle {
  val imm = UInt(32.W)
  val mem = UInt(4.W)
}
