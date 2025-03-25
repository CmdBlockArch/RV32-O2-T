package core.exec

import chisel3._
import core.dispatch.LsuBundle
import core.issue.Issue
import core.wb.{LsuWbBundle, WbBundle}
import utils.PiplineModule

class Lsu extends PiplineModule(new Issue.OutBundle(new LsuBundle), new LsuWbBundle) {
  val addr = cur.src1 + cur.inst.imm
  val mem = cur.inst.mem
  val data = cur.src2

  res.robIdx := cur.inst.robIdx
  res.rd := cur.inst.rd
  res.rdVal := DontCare

  // TODO: 真正的访存单元
  // 现在的访存全部当作MMIO定序
  res.mmio := true.B
  res.mmioOp := mem
  res.mmioAddr := addr
  res.mmioData := data
}
