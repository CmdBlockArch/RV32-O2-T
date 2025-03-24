package core.exec

import chisel3._
import chisel3.util._
import core.dispatch.LsuBundle
import core.issue.Issue
import core.wb.WbBundle
import perip.{AxiReadArb, AxiWriteArb}
import utils.PiplineModule

/* 乱序处理器中，提交之前所有指令都是推测状态，不能直接这样做访存执行单元！
 * 但是这个模块已经写好了，先留在这里以便后续参考
 * 另：当前PiplineModule的设计要求状态机冲刷必须在当周期完成
 * 对于访存单元，一旦访存请求发出，即使是flush也不能直接丢弃
 * 为了支持flush，必须在访存请求发出后，将请求信号保存在寄存器中，以保持请求期间信号不变
 * 所以这里的flush处理没做
 */

class UselessLsu extends PiplineModule(new Issue.OutBundle(new LsuBundle), new WbBundle) {
  val arbRead = IO(new AxiReadArb.ReadIO)
  val arbWrite = IO(new AxiWriteArb.WriteIO)

  val addr = cur.src1 + cur.inst.imm
  val mem = cur.inst.mem
  val data = cur.src2

  import Lsu.State._
  val state = RegInit(stIdle)
  val idle = state === stIdle
  val hold = state === stHold

  val dataValid = RegInit(false.B)
  val rdVal = Reg(UInt(32.W))

  when (idle && valid) {
    when (mem(3)) {
      state := stRead
    } .otherwise {
      state := stWrite
      dataValid := true.B
    }
  }
  when (arbRead.resp) {
    state := stHold
    rdVal := arbRead.data
  }
  when (arbWrite.dataReady) { dataValid := false.B }
  when (arbWrite.resp) { state := stHold }
  when (out.fire) { state := stIdle }

  setOutCond(hold)
  when (flush) {
    // 写不出来！
  }

  res.robIdx := cur.inst.robIdx
  res.rd := cur.inst.rd
  res.rdVal := rdVal

  arbRead.req := state === stRead
  arbRead.addr := addr
  arbRead.size := mem(1, 0)
  arbRead.burst := 0.U
  arbRead.len := 0.U

  arbWrite.req := state === stWrite
  arbWrite.addr := addr
  arbWrite.size := mem(1, 0)
  arbWrite.burst := 0.U
  arbWrite.len := 0.U
  arbWrite.dataValid := dataValid
  arbWrite.data := cur.src2 << Cat(addr(1, 0), 0.U(3.W))
  arbWrite.strb := Cat(Fill(2, mem(1)), mem(1, 0).orR, 1.U(1.W)) << addr(1, 0)
  arbWrite.last := true.B
}

object Lsu {
  object State extends ChiselEnum {
    val stIdle, stRead, stWrite, stHold = Value
  }
}
