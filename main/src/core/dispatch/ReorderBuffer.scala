package core.dispatch

import chisel3._
import chisel3.util._
import conf.Conf.{commitWidth, dispatchWidth, prfW, robN, robW, wbWidth}
import utils._

class ReorderBuffer extends Module {
  import ReorderBuffer._

  val dispatch = IO(Flipped(new DispatchIO))
  val wb = IO(Vec(wbWidth, Flipped(new WbIO)))
  val commit = IO(Flipped(new CommitIO))
  val io = IO(new Bundle {
    val flush = Input(Bool())
  })

  val rob = Reg(Vec(robN, new Entry))
  val count = RegInit(0.U(cntW.W))
  val enqPtr = RegInit(0.U(robW.W))
  val deqPtr = RegInit(0.U(robW.W))

  // ---------- dispatch ----------
  dispatch.freeCnt := robN.U - count
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
      rob(enqPtr + i.U).wb.trivial := true.B
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
  commit.count := count
  for (i <- 0 until commitWidth) {
    commit.entry(i) := rob(deqPtr + i.U)
  }
  deqPtr := deqPtr + commit.deqCnt

  // ---------- count & flush ----------
  when (io.flush) {
    count := 0.U
    enqPtr := 0.U
    deqPtr := 0.U
  } .otherwise {
    count := (count + enqCnt) - commit.deqCnt
  }
}

object ReorderBuffer {
  val cntW = log2Ceil(robN + 1)

  class DispatchBundle extends Bundle {
    val pc = PC()
    val arfRd = UInt(5.W)
    val prfRd = UInt(prfW.W)
    val ebreak = Bool()
    val wb = Bool()
  }

  class WbBundle extends Bundle {
    val trivial = Bool()
    val addr = UInt(32.W) // jmpPC or mmioAddr

    val mmio = Bool()
    val mmioOp = UInt(4.W)
    val mmioData = UInt(32.W)

    def jmp = !trivial && !mmio
  }

  class Entry extends Bundle {
    val dp = new DispatchBundle
    val wb = new WbBundle
  }

  class DispatchIO extends Bundle {
    val freeCnt = Input(UInt(cntW.W))
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
    val count = Input(UInt(cntW.W))
    val entry = Input(Vec(commitWidth, new Entry))

    val deqCnt = Output(UInt(robW.W))
  }
}
