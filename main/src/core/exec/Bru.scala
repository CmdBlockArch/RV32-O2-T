package core.exec

import chisel3._
import core.dispatch.BruBundle
import core.issue.Issue
import core.wb.BruWbBundle
import utils._

class Bru extends PiplineModule(new Issue.OutBundle(new BruBundle), new BruWbBundle){
  val rdSel = cur.inst.rdSel
  val pc = cur.inst.pc.full
  val src1 = cur.src1
  val src2 = cur.src2
  val imm = cur.inst.imm
  val func = cur.inst.func

  val br = rdSel === 0.U
  val jal = rdSel === "b10".U // JAL or JALR
  val jalr = cur.inst.jalr

  val rdA = Mux(rdSel(0), imm, 4.U)
  val rdB = Mux(rdSel(1), pc, 0.U)

  res.robIdx := cur.inst.robIdx
  res.rd := cur.inst.rd
  res.rdVal := rdA + rdB

  val a = src1
  val b = (~src2).asUInt
  val c = (a +& b) + 1.U

  val cf = c(32)
  val sf = c(31)
  val of = (a(31) === b(31)) && (sf ^ a(31))

  val lts = sf ^ of
  val ltu = !cf
  val eq = !(src1 ^ src2).orR

  val t = Mux(func(2), Mux(func(1), ltu, lts), eq).asBool
  val brj = br && (t ^ func(0))

  val jmp = jal || brj
  res.jmp := jmp

  val snpc = pc + 4.U
  val jnpc = Mux(jalr, src1, pc) + imm
  res.jmpPC := PC(Mux(jmp, jnpc, snpc))
  /* 此处产生跳转地址时，直接清除了低两位
   * 理论上来说，这样做不符合RISCV规范，也不会产生指令非对齐异常
   * 但是C拓展就是史，我也不打算实现，所以就这样吧 */
}
