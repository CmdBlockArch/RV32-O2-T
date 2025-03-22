package core.issue

import chisel3._
import utils._
import Issue._
import core.wb.PhyRegFile

class PrfRead[T <: Data](payload: => T)
  extends PiplineModule(new InstBundle(payload), new OutBundle(payload)) {

  val prfRead = IO(Vec(2, new PhyRegFile.ReadIO))

  res.inst := cur.inst

  prfRead(0).en := valid
  prfRead(1).en := valid
  prfRead(0).rs := cur.rs1
  prfRead(1).rs := cur.rs2
  res.src1 := prfRead(0).src
  res.src2 := prfRead(1).src
}
