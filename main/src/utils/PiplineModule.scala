package utils

import chisel3._
import chisel3.util._

abstract class PiplineModule[TI <: Data, TO <: Data]
(val inType: TI, val outType: TO) extends Module {
  val in = IO(Flipped(Decoupled(inType)))
  val out = IO(Decoupled(outType))
  val flush = IO(Input(Bool()))

  val valid = RegInit(false.B)
  val cur = Reg(inType)
  val res = out.bits

  val outCond = WireDefault(true.B)

  // 流水级处理每一次数据期间，update都只保持一周期为高，且该周期outCond为高
  // 用于更新存储器的状态，避免在流水级阻塞时重复多次更新
  // 如果使用out.fire作为某些存储器更新的条件，考虑到流水线反压串扰，时序会很差
  val updateReg = RegInit(false.B)
  when (updateReg && outCond) {
    updateReg := false.B
    // 若有新数据到来，会被下面的更新覆盖
  }
  val update = updateReg && outCond

  in.ready := !valid || (out.ready && outCond)
  out.valid := valid && outCond
  when (in.fire) {
    valid := !flush
    updateReg := !flush
    cur := in.bits
  } .elsewhen (out.fire || flush) {
    valid := false.B
    updateReg := false.B
  }

  def setOutCond(cond: Bool) = {
    outCond := cond
  }
}
