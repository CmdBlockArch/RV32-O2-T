package core.exec

import chisel3._
import chisel3.util._
import core.dispatch.BruBundle
import core.exec.mdu.{Mul, Div}
import core.issue.Issue
import core.wb.WbBundle
import utils._

class Mdu extends PiplineModule(new Issue.OutBundle(new BruBundle), new WbBundle) {
  val src1 = cur.src1
  val src2 = cur.src2
  val func = cur.inst.func

  val mul = Module(new Mul)
  val div = Module(new Div)

  mul.in.valid := valid && !func(2)
  mul.in.sign := Cat(func(1, 0).xorR, !func(1) && func(0))
  mul.in.a := src1
  mul.in.b := src2
  mul.out.ready := out.ready
  mul.flush := flush
  val mulVal = Mux(func(1, 0).orR, mul.out.prod(63, 32), mul.out.prod(31, 0))

  val divOf = src1 === Cat(1.U(1.W), 0.U(31.W)) && src2.andR
  val divZero = src2 === 0.U
  div.in.valid := valid && func(2) && !divZero && (!divOf || func(0))
  div.in.sign := !func(0)
  div.in.a := src1
  div.in.b := src2
  div.out.ready := out.ready
  div.flush := flush
  val divVal = MuxLookup1H(func(1, 0))(Seq(
    "b00".U -> Mux(divOf || divZero, Cat(1.U(1.W), Fill(31, divZero)), div.out.quot),
    "b01".U -> Mux(divZero, Fill(32, 1.U(1.W)), div.out.quot),
    "b10".U -> Mux(divOf || divZero, cur.src1 & Fill(32, divZero), div.out.rem),
    "b11".U -> Mux(divZero, cur.src1, div.out.rem),
  ))

  setOutCond(Mux(func(2), div.out.valid, mul.out.valid))
  res.robIdx := cur.inst.robIdx
  res.rd := cur.inst.rd
  res.rdVal := Mux(func(2), divVal, mulVal)
}
