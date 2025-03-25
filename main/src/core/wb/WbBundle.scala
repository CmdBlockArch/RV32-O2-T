package core.wb

import chisel3._
import conf.Conf.{prfW, robW}
import utils._

class WbBundle extends Bundle {
  val robIdx = UInt(robW.W)
  val rd = UInt(prfW.W)
  val rdVal = UInt(32.W)
}

class BruWbBundle extends WbBundle {
  val jmp = Bool()
  val jmpPC = PC()
}

class LsuWbBundle extends WbBundle {
  val mmio = Bool()
  val mmioOp = UInt(4.W)
  val mmioAddr = UInt(32.W)
  val mmioData = UInt(32.W)
}
