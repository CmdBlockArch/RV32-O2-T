package core.issue

import chisel3._
import chisel3.util._
import conf.Conf.{prfW, wbWidth}

class ResvStation[T <: Data](entryN: Int, payload: => T) extends Module {
  import Issue._
  val entryW = log2Ceil(entryN)
  val cntW = log2Ceil(entryN + 1)
  val instBundle = () => new InstBundle(payload)
  val dispatchBundle = () => new DispatchBundle(payload)
  val dispatchIO = () => new DispatchIO(payload)

  val dispatch = IO(Flipped(dispatchIO()))
  val out = IO(Decoupled(instBundle()))
  val wbRd = IO(Input(Vec(wbWidth, UInt(prfW.W))))
  val io = IO(new Bundle {
    val flush = Input(Bool())
  })

  // 写入指令重排
  val inValid = VecInit(Seq(
    dispatch.valid.reduce(_ || _),
    dispatch.valid.reduce(_ && _)
  ))
  val inEntry = VecInit(Seq(
    Mux(dispatch.valid(0), dispatch.entry(0), dispatch.entry(1)),
    dispatch.entry(1)
  ))

  // 保留站表项
  val rs = Reg(Vec(entryN, dispatchBundle()))

  // 空位数量
  val freeCnt = RegInit(entryN.U(cntW.W))
  val dispatchCnt = dispatch.valid.count(_.asBool)
  assert(dispatchCnt <= freeCnt) // 检查溢出
  freeCnt := (freeCnt + dispatchCnt) - out.fire
  dispatch.freeCnt := freeCnt
  when (io.flush) {
    freeCnt := entryN.U
  }

  // 唤醒
  val rsWaken = WireDefault(rs)
  for (i <- 0 until entryN) {
    rsWaken(i).rs1Ready := rs(i).rs1Ready || wbRd.contains(rs(i).rs1)
    rsWaken(i).rs2Ready := rs(i).rs2Ready || wbRd.contains(rs(i).rs2)
  }
}
