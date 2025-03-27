package core.dispatch

import chisel3._
import conf.Conf.{commitWidth, dispatchWidth, prfW, robN, robW, wbWidth}
import utils._

class ReorderBuffer extends Module {
  import ReorderBuffer._

  val dispatch = IO(Flipped(new DispatchIO))
  val wb = IO(Vec(wbWidth, Flipped(new WbIO)))
  val commit = IO(Flipped(new CommitIO))

  val rob = Reg(Vec(robN, new Entry))
  val freeCnt = RegInit(robN.U((robW + 1).W))
  val enqPtr = RegInit(0.U(robW.W))
  val deqPtr = RegInit(0.U(robW.W))

  // ---------- dispatch ----------
  dispatch.freeCnt := freeCnt
  dispatch.enqPtr := enqPtr
  val enqCnt = dispatch.valid.count(_.asBool)
  enqPtr := enqPtr + enqCnt

  // 不能出现后一个valid但前一个invalid的情况
  assert(dispatchWidth == 2)
  assert(dispatch.valid(0) || !dispatch.valid(1))

  // 写入ROB
  for (i <- 0 until dispatchWidth) {
    when (dispatch.valid(i)) {
      rob(enqPtr + i.U).dp := dispatch.entry(i)
      rob(enqPtr + i.U).wb.jmp := false.B
      rob(enqPtr + i.U).wb.mmio := false.B
    }
  }

  // --------- write back ----------
  for (i <- 0 until wbWidth) {
    when (wb(i).valid) {
      rob(wb(i).robIdx).dp.wb := true.B
      rob(wb(i).robIdx).wb := wb(i).entry
    }
  }

  // --------- commit ----------
  commit.freeCnt := freeCnt
  for (i <- 0 until commitWidth) {
    commit.entry(i) := rob(deqPtr + i.U)
  }
  deqPtr := deqPtr + commit.deqCnt

  // ---------- freeCnt ----------
  freeCnt := (freeCnt + commit.deqCnt) - enqCnt
}

object ReorderBuffer {
  class DispatchBundle extends Bundle {
    val pc = PC()
    val arfRd = UInt(5.W)
    val prfRd = UInt(prfW.W)
    val wb = Bool()
  }

  class WbBundle extends Bundle {
    val jmp = Bool()
    val jmpPc = PC()

    val mmio = Bool()
    val mmioOp = UInt(4.W)
    val mmioAddr = UInt(32.W)
    val mmioData = UInt(32.W)

    def trivial = !jmp && !mmio
  }

  class Entry extends Bundle {
    val dp = new DispatchBundle
    val wb = new WbBundle
  }

  class DispatchIO extends Bundle {
    val freeCnt = Input(UInt((robW + 1).W))
    val enqPtr = Input(UInt(robW.W))

    val valid = Output(Vec(dispatchWidth, Bool()))
    val entry = Output(Vec(dispatchWidth, new DispatchBundle))
  }

  class WbIO extends Bundle {
    val valid = Output(Bool())
    val robIdx = Output(UInt(robW.W))
    val entry = Output(new WbBundle)
  }

  class CommitIO extends Bundle {
    val freeCnt = Input(UInt((robW + 1).W))
    val entry = Input(Vec(commitWidth, new Entry))

    val deqCnt = Output(UInt(robW.W))

    def cnt = {
      val res = robN.U - freeCnt
      assert(res.getWidth == robW + 1)
      res
    }
  }
}
