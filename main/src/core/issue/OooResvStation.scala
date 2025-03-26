package core.issue

import chisel3._

class OooResvStation[T <: Data](entryN: Int, payload: => T)
  extends ResvStation(entryN, payload) {

  val valid = RegInit(VecInit(Seq.fill(entryN)(false.B)))

  // ---------- 输出 ----------
  val choice = Wire(Vec(entryN, new Bundle {
    val k = Bool()
    val v = instBundle()
  }))
  for (i <- 0 until entryN) {
    choice(i).k := valid(i) && rs(i).ready
    choice(i).v := rs(i)
  }
  val ready = VecInit(choice.map(_.k))
  out.valid := ready.reduce(_ || _)
  out.bits := choice.reduceTree((a, b) => Mux(a.k, a, b)).v

  // ---------- 唤醒 & 压缩 ----------
  // 输出压缩后，输入前，valid的状态
  val validShift = WireDefault(valid)
  when (out.fire) {
    // 直接将valid的状态向低位移动一位
    for (i <- 1 until entryN) {
      validShift(i - 1) := valid(i)
    }
    validShift(entryN - 1) := false.B
  }

  // 移动唤醒后的保留站表项
  for (i <- 0 until entryN - 1) {
    when (out.fire && ready.take(i + 1).reduce(_ || _)) {
      rs(i) := rsWaken(i + 1)
    } .otherwise {
      rs(i) := rsWaken(i)
    }
  }

  // ---------- 写入 ----------
  // 输出输入后，valid的状态
  val validNext = WireDefault(validShift)

  // 指令0写入
  when (validShift(0) === 0.U && inValid(0)) {
    validNext(0) := true.B
    rs(0) := inEntry(0)
  }
  for (i <- 1 until entryN) {
    when (validShift(i) === 0.U && validShift(i - 1) === 1.U && inValid(0)) {
      validNext(i) := true.B
      rs(i) := inEntry(0)
    }
  }

  // 指令1写入
  when (validShift(0) === 0.U && inValid(1)) {
    validNext(1) := true.B
    rs(1) := inEntry(1)
  }
  for (i <- 2 until entryN) {
    when (validShift(i - 1) === 0.U && validShift(i - 2) === 1.U && inValid(1)) {
      validNext(i) := true.B
      rs(i) := inEntry(1)
    }
  }

  // valid的状态更新
  valid := validNext

  // ---------- 冲刷 ----------
  when (io.flush) {
    valid := 0.U.asTypeOf(valid)
  }
}