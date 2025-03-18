package core.dispatch

import chisel3._
import chisel3.util.Decoupled
import conf.Conf.dispatchWidth
import core.rename.Rename

class Dispatch extends Module {
  val in = IO(Flipped(Decoupled(new Rename.OutBundle)))
  val robAlloc = IO(new ReorderBuffer.DispatchIO)
  val io = IO(new Bundle {
    val flush = Input(Bool())
  })

  val valid = RegInit(false.B)
  val cur = Reg(new Rename.OutBundle)
  val ready = WireDefault(false.B) // TODO: cond
  val update = valid && ready

  in.ready := !valid || ready
  when (in.fire) {
    valid := !io.flush
    cur := in.bits
  } .elsewhen (update || io.flush) {
    valid := false.B
  }

  // ---------- 分配ROB表项 ----------
  val dispatchCnt = cur.valid.count(_.asBool)
  val robCanAlloc = robAlloc.freeCnt >= dispatchCnt

  robAlloc.valid := VecInit(cur.valid.map(update && _))
  for (i <- 0 until dispatchWidth) {
    robAlloc.entry(i).pc := cur.pc.next(i)
    robAlloc.entry(i).arfRd := cur.gpr(i).ard
    robAlloc.entry(i).prfRd := cur.gpr(i).rd
    robAlloc.entry(i).wb := cur.inst(i).err // 异常指令可以直接提交
  }

}
