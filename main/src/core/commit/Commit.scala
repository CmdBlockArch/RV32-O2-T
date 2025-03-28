package core.commit

import chisel3._
import chisel3.util._
import conf.Conf.{commitWidth, debug, prfW, robW}
import core.dispatch.ReorderBuffer
import core.wb.PhyRegFile
import perip.{AxiReadArb, AxiWriteArb}
import utils._
import utils.Debug._

class Commit extends Module {
  val rob = IO(new ReorderBuffer.CommitIO)
  val prfFree = IO(Output(Vec(commitWidth, UInt(prfW.W))))
  val prfWrite = IO(new PhyRegFile.WriteIO)
  val arbRead = IO(new AxiReadArb.ReadIO)
  val arbWrite = IO(new AxiWriteArb.WriteIO)
  val io = IO(new Bundle {
    val redirect = Output(Bool())
    val redirectPC = Output(PC())

    val rat = Output(Vec(32, UInt(prfW.W)))
  })

  val valid = RegInit(VecInit(Seq.fill(commitWidth)(false.B)))
  val entry = Reg(Vec(commitWidth, new ReorderBuffer.Entry))

  assert(commitWidth == 2)
  assert(valid(0) || !valid(1)) // 不能出现后一个valid但前一个invalid的情况

  // 状态机
  import Commit.State._
  val state = RegInit(stIdle)
  val idle = state === stIdle
  val hold = state === stHold

  // 如果第一条指令是trivial的，那么第二条指令要么不存在，要么也是trivial的
  val ready = entry(0).wb.trivial || hold

  // ---------- ROB读取 ----------
  val robH1 = rob.count.orR && rob.entry(0).dp.wb
  val robH2 = rob.count(robW, 1).orR && rob.entry(0).dp.wb && rob.entry(1).dp.wb
  val robR2 = rob.entry(0).wb.trivial && rob.entry(1).wb.trivial

  when (!valid(0) || ready) { // 可以接收新指令
    when (robH1) { // 存在至少一条可提交的指令
      valid(0) := true.B
      entry(0) := rob.entry(0)
      when (robH2 && robR2) { // 两条指令都可以提交
        rob.deqCnt := 2.U
        valid(1) := true.B
        entry(1) := rob.entry(1)
      } .otherwise { // 只能提交一条指令
        rob.deqCnt := 1.U
        valid(1) := false.B
      }
    } .otherwise { // 没有可提交的指令
      rob.deqCnt := 0.U
      valid(0) := false.B
      valid(1) := false.B
    }
  } .otherwise {
    rob.deqCnt := 0.U
  }

  // 只有当两条指令都是trivial时，才可以一周期提交两条指令
  when (valid(0)) {
    assert(!valid(1) || (entry(0).wb.trivial && entry(1).wb.trivial))
    when (entry(0).wb.trivial) {
      assert(!valid(1) || entry(1).wb.trivial)
    }
  }

  // ---------- 重命名维护 ----------
  val rat = RegInit(VecInit(Seq.fill(32)(0.U(prfW.W))))
  assert(rat(0.U) === 0.U)

  // 更新确定状态的RAT
  when (idle) {
    for (i <- 0 until commitWidth) {
      when (valid(i) && entry(i).dp.arfRd =/= 0.U) {
        assert(entry(i).dp.prfRd =/= 0.U)
        rat(entry(i).dp.arfRd) := entry(i).dp.prfRd
      }
    }
  }

  // 释放物理寄存器
  prfFree := valid.zip(entry).map{ case (v, e) =>
    Mux(idle && v, rat(e.dp.arfRd), 0.U)
  }

  // ---------- 状态机 ----------
  when (hold) {
    state := stIdle
  }

  // ---------- 分支预测失败恢复 ----------
  when (idle && valid(0) && entry(0).wb.jmp) {
    state := stJmp
  }
  val doRedirect = state === stJmp
  when (doRedirect) {
    state := stHold
  }
  io.redirect := doRedirect
  io.redirectPC := PC(entry(0).wb.addr)
  io.rat := rat

  // ---------- MMIO ----------
  val memEn = valid(0) && entry(0).wb.mmio
  val mem = entry(0).wb.mmioOp
  val addr = entry(0).wb.addr
  val data = entry(0).wb.mmioData
  val dataValid = RegInit(false.B)
  val rdVal = Reg(UInt(32.W))
  when (idle && memEn) {
    when (mem(3)) {
      state := stRead
    } .otherwise {
      state := stWrite
      dataValid := true.B
    }
  }
  when (arbRead.resp) {
    state := stHold
    rdVal := arbRead.data
  }
  when (arbWrite.dataReady) { dataValid := false.B }
  when (arbWrite.resp) { state := stHold }

  prfWrite.en := hold && memEn
  prfWrite.rd := entry(0).dp.prfRd
  prfWrite.data := rdVal

  arbRead.req := state === stRead
  arbRead.addr := addr
  arbRead.size := mem(1, 0)
  arbRead.burst := 0.U
  arbRead.len := 0.U

  arbWrite.req := state === stWrite
  arbWrite.addr := addr
  arbWrite.size := mem(1, 0)
  arbWrite.burst := 0.U
  arbWrite.len := 0.U
  arbWrite.dataValid := dataValid
  arbWrite.data := data << Cat(addr(1, 0), 0.U(3.W))
  arbWrite.strb := Cat(Fill(2, mem(1)), mem(1, 0).orR, 1.U(1.W)) << addr(1, 0)
  arbWrite.last := true.B

  // ---------- debug ----------
  val dbgCommit = DebugRegNext(valid(0) && ready, false.B)
  val dbgValid = DebugRegNext(valid)
  val dbgEntry = DebugRegNext(entry)
  val dbgOut = DebugIO(new Bundle {
    val commit = Output(Bool())
    val valid = Output(Vec(commitWidth, Bool()))
    val entry = Output(Vec(commitWidth, new ReorderBuffer.Entry))
    val rat = Output(Vec(32, UInt(prfW.W)))
  })
  if (debug) {
    dbgOut.get.commit := dbgCommit.get
    dbgOut.get.valid := dbgValid.get
    dbgOut.get.entry := dbgEntry.get
    dbgOut.get.rat := rat
  }
}

object Commit {
  object State extends ChiselEnum {
    val stIdle, stRead, stWrite, stHold, stJmp = Value
  }
}
