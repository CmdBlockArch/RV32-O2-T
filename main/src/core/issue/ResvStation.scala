package core.issue

import chisel3._
import chisel3.experimental.dataview._
import chisel3.util._
import conf.Conf.{prfW, wbWidth}

class ResvStation[T <: Data](entryN: Int, payload: => T) extends Module {
  import Issue._
  val instBundle = () => new InstBundle(payload)
  val dispatchBundle = () => new DispatchBundle(payload)
  val dispatchIO = () => new DispatchIO(payload)

  val dispatch = IO(Flipped(dispatchIO()))
  val out = IO(Decoupled(instBundle()))
  val wbRd = IO(Input(Vec(wbWidth, UInt(prfW.W))))
  val io = IO(new Bundle {
    val flush = Input(Bool())
  })

  val valid = RegInit(VecInit(Seq.fill(entryN)(false.B)))
  val rs = Reg(Vec(entryN, dispatchBundle()))

  // ---------- 输出 ----------
  val choice = Wire(Vec(entryN, new Bundle {
    val k = Bool()
    val v = instBundle()
  }))
  for (i <- 0 until entryN) {
    choice(i).k := valid(i) && rs(i).ready
    choice(i).v := rs(i).viewAsSupertype(instBundle())
  }
  val ready = VecInit(choice.map(_.k))
  out.valid := ready.reduce(_ || _)
  out.bits := choice.reduceTree((a, b) => Mux(a.k, a, b)).v

  // ---------- 唤醒 ----------
  val rsWaken = WireDefault(rs)
  for (i <- 0 until entryN) {
    rsWaken(i).rs1Ready := rs(i).rs1Ready || wbRd.contains(rs(i).rs1)
    rsWaken(i).rs2Ready := rs(i).rs2Ready || wbRd.contains(rs(i).rs2)
  }

  // ---------- 压缩 ----------
  // 输出压缩后，输入前，valid的状态
  val validShift = WireDefault(valid)
  when (out.fire) {
    // 直接将valid的状态向低位移动一位
    for (i <- 1 until entryN) {
      validShift(i - 1) := valid(i)
    }
    validShift(entryN - 1) := false.B
  }

  // 移动保留站表项
  for (i <- 0 until entryN - 1) {
    when (out.fire && ready.take(i + 1).reduce(_ || _)) {
      rs(i) := rsWaken(i + 1)
    } .otherwise {
      rs(i) := rsWaken(i)
    }
  }

  // ---------- 写入 ----------
  // 保留站空位数量
  dispatch.freeCnt := valid.count(!_.asBool) +& out.fire
  assert(dispatch.freeCnt +& dispatch.valid.count(_.asBool) <= entryN.U)

  // 写入指令重排
  val inValid = VecInit(Seq(
    dispatch.valid.reduce(_ || _),
    dispatch.valid.reduce(_ && _)
  ))
  val inEntry = VecInit(Seq(
    Mux(dispatch.valid(0), dispatch.entry(0), dispatch.entry(1)),
    dispatch.entry(1)
  ))

  // 输出输入后，valid的状态
  val validNext = WireDefault(validShift)

  // 指令0写入
  when (valid(0) === 0.U && inValid(0)) {
    validNext(0) := true.B
    rs(0) := inEntry(0)
  }
  for (i <- 1 until entryN) {
    when (valid(i) === 0.U && valid(i - 1) === 1.U && inValid(0)) {
      validNext(i) := true.B
      rs(i) := inEntry(0)
    }
  }

  // 指令1写入
  when (valid(0) === 0.U && inValid(1)) {
    validNext(1) := true.B
    rs(1) := inEntry(1)
  }
  for (i <- 2 until entryN) {
    when (valid(i - 1) === 0.U && valid(i - 2) === 1.U && inValid(1)) {
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
