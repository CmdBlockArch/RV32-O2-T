package core.dispatch

import chisel3._
import conf.Conf.{dispatchWidth, prfW, robN, robW, wbWidth}
import utils._

class ReorderBuffer extends Module {
  import ReorderBuffer._

  val dispatch = IO(Flipped(new DispatchIO))
  val wb = IO(Vec(wbWidth, Flipped(new WbIO)))

  val rob = Reg(Vec(robN, new Entry))
  val freeCnt = RegInit(robN.U((robW + 1).W))
  val enqPtr = RegInit(0.U(robW.W))
  val deqPtr = RegInit(0.U(robW.W))

  // ---------- dispatch ----------
  dispatch.freeCnt := freeCnt
  dispatch.enqPtr := enqPtr
  enqPtr := enqPtr + dispatch.valid.count(_.asBool)

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
  // TODO: 提交逻辑
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
}
