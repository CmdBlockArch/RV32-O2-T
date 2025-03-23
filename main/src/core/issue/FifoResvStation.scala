package core.issue

import chisel3._

class FifoResvStation[T <: Data](entryN: Int, payload: => T)
  extends ResvStation(entryN, payload) {

  val enqPtr = RegInit(0.U(entryW.W))
  val deqPtr = RegInit(0.U(entryW.W))

  // ---------- 输出 ----------
  out.valid := freeCnt =/= entryN.U && rs(deqPtr).ready
  out.bits := rs(deqPtr)

  when (out.fire) {
    deqPtr := deqPtr + 1.U
  }

  // ---------- 唤醒 ----------
  /* - 关于唤醒
   * 理论上来说，顺序发射队列可以不做唤醒电路，
   * 只需要在输出时，对寄存器堆状态进行检查。
   * 但由于顺序发射队列只用于调试，以及在早期实现中确保访存正确性，
   * 故采用和乱序保留站一样的唤醒电路，统一不同保留站的接口。
   */
  rs := rsWaken

  // ---------- 写入 ----------
  when (inValid(0)) { rs(enqPtr) := inEntry(0) }
  when (inValid(1)) { rs(enqPtr + 1.U) := inEntry(1) }

  enqPtr := enqPtr + dispatchCnt

  // ---------- 冲刷 ----------
  when (io.flush) {
    enqPtr := 0.U
    deqPtr := 0.U
  }
}
