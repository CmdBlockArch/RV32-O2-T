package core.dispatch

import chisel3._
import chisel3.util.Decoupled
import conf.Conf.dispatchWidth
import core.issue.Issue
import core.rename.Rename
import core.wb.PhyRegFile

class Dispatch extends Module {
  val in = IO(Flipped(Decoupled(new Rename.OutBundle)))
  val robAlloc = IO(new ReorderBuffer.DispatchIO)
  val prfProbe = IO(Vec(dispatchWidth * 2, new PhyRegFile.ProbeIO))
  val io = IO(new Bundle {
    val flush = Input(Bool())
  })

  val alu = IO(Vec(2, new Issue.DispatchIO(new AluBundle)))
  val bru = IO(new Issue.DispatchIO(new BruBundle))
  val mdu = IO(new Issue.DispatchIO(new MduBundle))
  val lsu = IO(new Issue.DispatchIO(new LsuBundle))

  val valid = RegInit(false.B)
  val cur = Reg(new Rename.OutBundle)
  val ready = WireDefault(false.B)
  val update = valid && ready

  in.ready := !valid || ready
  when (in.fire) {
    valid := !io.flush
    cur := in.bits
  } .elsewhen (update || io.flush) {
    valid := false.B
  }

  // ---------- 分配ROB表项 ----------
  val dispatchCnt = cur.valid.count(_.asBool)
  val robReady = robAlloc.freeCnt >= dispatchCnt

  robAlloc.valid := VecInit(cur.valid.map(update && _))
  for (i <- 0 until dispatchWidth) {
    robAlloc.entry(i).pc := cur.pc.next(i)
    robAlloc.entry(i).arfRd := cur.gpr(i).ard
    robAlloc.entry(i).prfRd := cur.gpr(i).rd
    robAlloc.entry(i).wb := cur.inst(i).err // 异常指令可以直接提交
  }

  // ---------- 物理寄存器状态 ----------
  val rs1Ready = Wire(Vec(dispatchWidth, Bool()))
  val rs2Ready = Wire(Vec(dispatchWidth, Bool()))
  for (i <- 0 until dispatchWidth) {
    prfProbe(i * 2).rs := cur.gpr(i).rs1
    prfProbe(i * 2 + 1).rs := cur.gpr(i).rs2
    rs1Ready(i) := prfProbe(i * 2).ready
    rs2Ready(i) := prfProbe(i * 2 + 1).ready
  }

  // ---------- 分派信息生成 ----------
  // ALU
  val aluReq = Wire(Vec(dispatchWidth, Bool()))
  val aluEntry = Wire(Vec(dispatchWidth, new Issue.DispatchBundle(new AluBundle)))
  for (i <- 0 until dispatchWidth) {
    aluReq(i) := cur.valid(i) && cur.inst(i).exu(0)

    aluEntry(i).rs1 := cur.gpr(i).rs1
    aluEntry(i).rs1Ready := rs1Ready(i)
    aluEntry(i).rs2 := cur.gpr(i).rs2
    aluEntry(i).rs2Ready := rs2Ready(i)

    aluEntry(i).inst.rd := cur.gpr(i).rd
    aluEntry(i).inst.robIdx := robAlloc.enqPtr + i.U

    aluEntry(i).inst.imm := cur.inst(i).imm
    aluEntry(i).inst.useImm := cur.inst(i).aluUseImm
    aluEntry(i).inst.func := cur.inst(i).func
    aluEntry(i).inst.sign := cur.inst(i).aluSign
  }

  // BRU
  val bruReq = Wire(Vec(dispatchWidth, Bool()))
  val bruEntry = Wire(Vec(dispatchWidth, new Issue.DispatchBundle(new BruBundle)))
  for (i <- 0 until dispatchWidth) {
    bruReq(i) := cur.valid(i) && cur.inst(i).exu(1)

    bruEntry(i).rs1 := cur.gpr(i).rs1
    bruEntry(i).rs1Ready := rs1Ready(i)
    bruEntry(i).rs2 := cur.gpr(i).rs2
    bruEntry(i).rs2Ready := rs2Ready(i)

    bruEntry(i).inst.rd := cur.gpr(i).rd
    bruEntry(i).inst.robIdx := robAlloc.enqPtr + i.U

    bruEntry(i).inst.imm := cur.inst(i).imm
    bruEntry(i).inst.func := cur.inst(i).func
    bruEntry(i).inst.rdSel := cur.inst(i).bruRdSel
    bruEntry(i).inst.jalr := cur.inst(i).bruJalr
  }

  // MDU
  val mduReq = Wire(Vec(dispatchWidth, Bool()))
  val mduEntry = Wire(Vec(dispatchWidth, new Issue.DispatchBundle(new MduBundle)))
  for (i <- 0 until dispatchWidth) {
    mduReq(i) := cur.valid(i) && cur.inst(i).exu(2)

    mduEntry(i).rs1 := cur.gpr(i).rs1
    mduEntry(i).rs1Ready := rs1Ready(i)
    mduEntry(i).rs2 := cur.gpr(i).rs2
    mduEntry(i).rs2Ready := rs2Ready(i)

    mduEntry(i).inst.rd := cur.gpr(i).rd
    mduEntry(i).inst.robIdx := robAlloc.enqPtr + i.U

    mduEntry(i).inst.func := cur.inst(i).func
  }

  // LSU
  val lsuReq = Wire(Vec(dispatchWidth, Bool()))
  val lsuEntry = Wire(Vec(dispatchWidth, new Issue.DispatchBundle(new LsuBundle)))
  for (i <- 0 until dispatchWidth) {
    lsuReq(i) := cur.valid(i) && cur.inst(i).exu(3)

    lsuEntry(i).rs1 := cur.gpr(i).rs1
    lsuEntry(i).rs1Ready := rs1Ready(i)
    lsuEntry(i).rs2 := cur.gpr(i).rs2
    lsuEntry(i).rs2Ready := rs2Ready(i)

    lsuEntry(i).inst.rd := cur.gpr(i).rd
    lsuEntry(i).inst.robIdx := robAlloc.enqPtr + i.U

    lsuEntry(i).inst.imm := cur.inst(i).imm
    lsuEntry(i).inst.mem := cur.inst(i).lsuMem
  }

  // ---------- 写保留站 ----------
  assert(dispatchWidth == 2)
  val aluSel = alu(1).freeCnt > alu(0).freeCnt
  val rsReady = alu(aluSel).freeCnt >= aluReq.count(_.asBool) &&
    bru.freeCnt >= bruReq.count(_.asBool) &&
    mdu.freeCnt >= mduReq.count(_.asBool) &&
    lsu.freeCnt >= lsuReq.count(_.asBool)

  alu(0).valid := aluReq.map(_ && update && !aluSel)
  alu(0).entry := aluEntry
  alu(1).valid := aluReq.map(_ && update && aluSel)
  alu(1).entry := aluEntry
  bru.valid := bruReq.map(_ && update)
  bru.entry := bruEntry
  mdu.valid := mduReq.map(_ && update)
  mdu.entry := mduEntry
  lsu.valid := lsuReq.map(_ && update)
  lsu.entry := lsuEntry

  // ---------- 分派条件 ----------
  ready := robReady && rsReady
}
