package core.fetch

import chisel3._
import chisel3.util._
import conf.Conf.fetchWidth
import utils._

class Fetch extends Module {

}

object Fetch {
  class InstBundle extends Bundle {
    val valid = Bool()
    val inst = UInt(32.W)
  }

  class OutBundle extends Bundle {
    val pc = PC()
    val inst = Vec(fetchWidth, new InstBundle)
  }
}
