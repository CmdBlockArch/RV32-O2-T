package core

import chisel3._
import utils._
import conf.Conf.debug
import core.fetch.Fetch
import perip.{AxiReadArb, AxiReadIO, AxiWriteArb, AxiWriteIO, SimMemRead, SimMemWrite}

class Top extends Module {
  val axiReadArb = Module(new AxiReadArb(1))
  // val axiWriteArb = Module(new AxiWriteArb(1))

  if (debug) {
    val simMemRead = Module(new SimMemRead)
    // val simMemWrite = Module(new SimMemWrite)
    simMemRead.io :<>= axiReadArb.slave
    // simMemWrite.io :<>= axiWriteArb.slave
  } else {
    val axiReadIO = IO(new AxiReadIO)
    // val axiWriteIO = IO(new AxiWriteIO)
    axiReadIO :<>= axiReadArb.slave
    // axiWriteIO :<>= axiWriteArb.slave
  }

  val fetch = Module(new Fetch)
  axiReadArb.master(0) :<>= fetch.arbRead
  fetch.io.redirect := false.B
  fetch.io.redirectPC := PC.resetPC

  val testOut = IO(new Fetch.OutBundle)
  testOut := fetch.out.bits
  fetch.out.ready := true.B
}
