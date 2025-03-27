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
  val alu0Exec = Module(new core.exec.Alu)
  val alu1Exec = Module(new core.exec.Alu)
  val bruExec = Module(new core.exec.Bru)
  val mduExec = Module(new core.exec.Mdu)
  val lsuExec = Module(new core.exec.Lsu)

  // 写回
  val alu0Wb = Module(new core.wb.WriteBack)
  val alu1Wb = Module(new core.wb.WriteBack)
  val bruWb = Module(new core.wb.BruWriteBack)
  val mduWb = Module(new core.wb.WriteBack)
  val lsuWb = Module(new core.wb.LsuWriteBack)

  // 提交
  val commit = Module(new core.commit.Commit)

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
  dispatch.wbRd := 0.U.asTypeOf(dispatch.wbRd)
  dispatch.io.flush := redirect
  alu0Issue.dispatch :<>= dispatch.alu(0)
  alu1Issue.dispatch :<>= dispatch.alu(1)
  bruIssue.dispatch :<>= dispatch.bru
  mduIssue.dispatch :<>= dispatch.mdu
  lsuIssue.dispatch :<>= dispatch.lsu

  // issue
  alu0Issue.io.flush := redirect
  alu0Issue.wbRd := prf.wbRd
  prf.read(0) :<>= alu0Issue.prfRead(0)
  prf.read(1) :<>= alu0Issue.prfRead(1)
  alu0Exec.in :<>= alu0Issue.out

  alu1Issue.io.flush := redirect
  alu1Issue.wbRd := prf.wbRd
  prf.read(2) :<>= alu1Issue.prfRead(0)
  prf.read(3) :<>= alu1Issue.prfRead(1)
  alu1Exec.in :<>= alu1Issue.out

  bruIssue.io.flush := redirect
  bruIssue.wbRd := prf.wbRd
  prf.read(4) :<>= bruIssue.prfRead(0)
  prf.read(5) :<>= bruIssue.prfRead(1)
  bruExec.in :<>= bruIssue.out

  mduIssue.io.flush := redirect
  mduIssue.wbRd := prf.wbRd
  prf.read(6) :<>= mduIssue.prfRead(0)
  prf.read(7) :<>= mduIssue.prfRead(1)
  mduExec.in :<>= mduIssue.out

  lsuIssue.io.flush := redirect
  lsuIssue.wbRd := prf.wbRd
  prf.read(8) :<>= lsuIssue.prfRead(0)
  prf.read(9) :<>= lsuIssue.prfRead(1)
  lsuExec.in :<>= lsuIssue.out

  // exec & wb
  alu0Exec.flush := redirect
  alu0Wb.in :<>= alu0Exec.out
  prf.write(0) :<>= alu0Wb.prfWrite
  rob.wb(0) :<>= alu0Wb.robWrite
  alu0Wb.io.flush := redirect

  alu1Exec.flush := redirect
  alu1Wb.in :<>= alu1Exec.out
  prf.write(1) :<>= alu1Wb.prfWrite
  rob.wb(1) :<>= alu1Wb.robWrite
  alu1Wb.io.flush := redirect

  bruExec.flush := redirect
  bruWb.in :<>= bruExec.out
  prf.write(2) :<>= bruWb.prfWrite
  rob.wb(2) :<>= bruWb.robWrite
  bruWb.io.flush := redirect

  mduExec.flush := redirect
  mduWb.in :<>= mduExec.out
  prf.write(3) :<>= mduWb.prfWrite
  rob.wb(3) :<>= mduWb.robWrite
  mduWb.io.flush := redirect

  lsuExec.flush := redirect
  lsuWb.in :<>= lsuExec.out
  prf.write(4) :<>= lsuWb.prfWrite
  rob.wb(4) :<>= lsuWb.robWrite
  lsuWb.io.flush := redirect

  // commit
  rob.commit :<>= commit.rob
  rename.prfFree := commit.prfFree
}
