package core.dispatch

import chisel3._
import chisel3.util._
import conf.Conf.{robN, robW, prfW, dispatchWidth}
import utils._

class ReorderBuffer extends Module {
  import ReorderBuffer._

  val dispatch = IO(Flipped(new DispatchIO))

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
  when (dispatch.valid(0)) {
    rob(enqPtr) := dispatch.entry(0)
  }
  when (dispatch.valid(1)) {
    rob(enqPtr + 1.U) := dispatch.entry(1)
  }
}

object ReorderBuffer {
  class Entry extends Bundle {
    val pc = PC()
    val arfRd = UInt(5.W)
    val prfRd = UInt(prfW.W)
    val wb = Bool()
  }

  class DispatchIO extends Bundle {
    val freeCnt = Input(UInt((robW + 1).W))
    val enqPtr = Input(UInt(robW.W))

    val valid = Output(Vec(dispatchWidth, Bool()))
    val entry = Output(Vec(dispatchWidth, new Entry))
  }
}
