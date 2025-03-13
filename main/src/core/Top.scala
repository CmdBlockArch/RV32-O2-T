package core

import chisel3._
import utils._
import conf.Conf.{debug, renameWidth}
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

  val fetch = Module(new core.fetch.Fetch)
  val decode = Module(new core.decode.Decode)
  val rename = Module(new core.rename.Rename)

  axiReadArb.master(0) :<>= fetch.arbRead
  decode.in :<>= fetch.out
  fetch.io.redirect := false.B
  fetch.io.redirectPC := PC.resetPC
  fetch.io.fenceI := false.B

  decode.flush := false.B
  rename.in :<>= decode.out

  rename.flush := false.B
  rename.out.ready := true.B

  val testOut = IO(new core.rename.Rename.OutBundle)
  testOut := rename.out.bits
}
