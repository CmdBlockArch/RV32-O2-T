package core.wb

import chisel3._
import chisel3.util._
import core.dispatch.ReorderBuffer

class WbCommon[T <: WbBundle](wbType: T) extends Module {
  val in = IO(Flipped(Decoupled(wbType)))
  val prfWrite = IO(new PhyRegFile.WriteIO)
  val robWrite = IO(new ReorderBuffer.WbIO)
  val io = IO(new Bundle {
    val flush = Input(Bool())
  })

  val valid = RegInit(false.B)
  val cur = Reg(wbType)

  in.ready := true.B
  when (in.fire) {
    valid := !io.flush
    cur := in.bits
  } .elsewhen (valid || io.flush) {
    valid := false.B
  }

  prfWrite.rd := cur.rd
  prfWrite.data := cur.rdVal

  robWrite.valid := valid
  robWrite.robIdx := cur.robIdx
  robWrite.entry := DontCare
}

class WriteBack extends WbCommon(new WbBundle) {
  prfWrite.en := valid

  robWrite.entry.trivial := true.B
  robWrite.entry.mmio := false.B
}

class BruWriteBack extends WbCommon(new BruWbBundle) {
  prfWrite.en := valid

  robWrite.entry.trivial := !cur.jmp
  robWrite.entry.addr := cur.jmpPC.full
  robWrite.entry.mmio := false.B
}

class LsuWriteBack extends WbCommon(new LsuWbBundle) {
  prfWrite.en := false.B

  robWrite.entry.trivial := !cur.mmio
  robWrite.entry.mmio := cur.mmio
  robWrite.entry.mmioOp := cur.mmioOp
  robWrite.entry.addr := cur.mmioAddr
  robWrite.entry.mmioData := cur.mmioData
}
