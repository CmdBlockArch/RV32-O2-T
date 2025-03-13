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

  // update从每次流水级接收到新数据开始，在(valid && outCond)的情况下只保持一周期的高电平
  // 用于更新存储器的状态，避免在流水级阻塞时重复多次更新
  // 如果使用out.fire作为某些存储器更新的条件，考虑到流水线反压串扰，时序会很差
  val update = RegInit(false.B)
  when (update && outCond) {
    update := false.B
    // 若有新数据到来，会被下面的更新覆盖
  }

  in.ready := !valid || (out.ready && outCond)
  out.valid := valid && outCond
  when (in.fire) {
    valid := !flush
    update := !flush
    cur := in.bits
  } .elsewhen (out.fire || flush) {
    valid := false.B
    update := false.B
  }

  def setOutCond(cond: Bool) = {
    outCond := cond
  }
}
