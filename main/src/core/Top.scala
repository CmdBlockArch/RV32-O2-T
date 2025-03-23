package core

import chisel3._
import utils._
import conf.Conf._
import core.dispatch._

class Top extends Module {
  // ---------- 总线 ----------
  val axiReadArb = Module(new perip.AxiReadArb(1))
  // val axiWriteArb = Module(new perip.AxiWriteArb(1))

  if (debug) {
    val simMemRead = Module(new perip.SimMemRead)
    // val simMemWrite = Module(new perip.SimMemWrite)
    simMemRead.io :<>= axiReadArb.slave
    // simMemWrite.io :<>= axiWriteArb.slave
  } else {
    val axiReadIO = IO(new perip.AxiReadIO)
    // val axiWriteIO = IO(new perip.AxiWriteIO)
    axiReadIO :<>= axiReadArb.slave
    // axiWriteIO :<>= axiWriteArb.slave
  }

  // ---------- 模块实例化 ----------
  // 全局信号
  val redirect = WireDefault(false.B)
  val fenceI = WireDefault(false.B)

  // 公用模块
  val rob = Module(new core.dispatch.ReorderBuffer)
  val prf = Module(new core.wb.PhyRegFile)
  prf.write := 0.U.asTypeOf(prf.write)

  // 顺序流水级
  val fetch = Module(new core.fetch.Fetch)
  val decode = Module(new core.decode.Decode)
  val rename = Module(new core.rename.Rename)
  val dispatch = Module(new core.dispatch.Dispatch)

  // 发射
  val alu0Issue = Module(new core.issue.Issue(aluRsN, new AluBundle))
  val alu1Issue = Module(new core.issue.Issue(aluRsN, new AluBundle))
  val bruIssue = Module(new core.issue.Issue(bruRsN, new BruBundle))
  val mduIssue = Module(new core.issue.Issue(mduRsN, new MduBundle))
  val lsuIssue = Module(new core.issue.Issue(lsuRsN, new LsuBundle, fifo=true))

  // 执行单元
  // TODO: 添加执行单元

  // ---------- 连线 ----------
  // fetch
  axiReadArb.master(0) :<>= fetch.arbRead
  decode.in :<>= fetch.out
  fetch.io.redirect := redirect
  fetch.io.redirectPC := PC.resetPC
  fetch.io.fenceI := fenceI

  // decode
  decode.flush := redirect
  rename.in :<>= decode.out

  // rename
  rename.flush := redirect
  dispatch.in :<>= rename.out

  // dispatch
  rob.dispatch :<>= dispatch.robAlloc
  prf.probe :<>= dispatch.prfProbe
  dispatch.io.flush := redirect
  alu0Issue.dispatch :<>= dispatch.alu(0)
  alu1Issue.dispatch :<>= dispatch.alu(1)
  bruIssue.dispatch :<>= dispatch.bru
  mduIssue.dispatch :<>= dispatch.mdu
  lsuIssue.dispatch :<>= dispatch.lsu

  // issue
  alu0Issue.io.flush := redirect
  alu0Issue.wbRd := 0.U.asTypeOf(alu0Issue.wbRd)
  prf.read(0) :<>= alu0Issue.prfRead(0)
  prf.read(1) :<>= alu0Issue.prfRead(1)

  alu1Issue.io.flush := redirect
  alu1Issue.wbRd := 0.U.asTypeOf(alu1Issue.wbRd)
  prf.read(2) :<>= alu1Issue.prfRead(0)
  prf.read(3) :<>= alu1Issue.prfRead(1)

  bruIssue.io.flush := redirect
  bruIssue.wbRd := 0.U.asTypeOf(bruIssue.wbRd)
  prf.read(4) :<>= bruIssue.prfRead(0)
  prf.read(5) :<>= bruIssue.prfRead(1)

  mduIssue.io.flush := redirect
  mduIssue.wbRd := 0.U.asTypeOf(mduIssue.wbRd)
  prf.read(6) :<>= mduIssue.prfRead(0)
  prf.read(7) :<>= mduIssue.prfRead(1)

  lsuIssue.io.flush := redirect
  lsuIssue.wbRd := 0.U.asTypeOf(lsuIssue.wbRd)
  prf.read(8) :<>= lsuIssue.prfRead(0)
  prf.read(9) :<>= lsuIssue.prfRead(1)

  // test output
  val alu0IssueOut = IO(new core.issue.Issue.OutBundle(new AluBundle))
  alu0Issue.out.ready := true.B
  alu0IssueOut := alu0Issue.out.bits
  val alu1IssueOut = IO(new core.issue.Issue.OutBundle(new AluBundle))
  alu1Issue.out.ready := true.B
  alu1IssueOut := alu1Issue.out.bits
  val bruIssueOut = IO(new core.issue.Issue.OutBundle(new BruBundle))
  bruIssue.out.ready := true.B
  bruIssueOut := bruIssue.out.bits
  val mduIssueOut = IO(new core.issue.Issue.OutBundle(new MduBundle))
  mduIssue.out.ready := true.B
  mduIssueOut := mduIssue.out.bits
  val lsuIssueOut = IO(new core.issue.Issue.OutBundle(new LsuBundle))
  lsuIssue.out.ready := true.B
  lsuIssueOut := lsuIssue.out.bits
}
