package perip

import chisel3._
import chisel3.util._

class MemReader(burstN: Int) extends Module {
  val arbRead = IO(new AxiReadArb.ReadIO)
  val io = IO(new Bundle {
    // input
    val req = Input(Bool())
    val addr = Input(UInt(32.W))
    // output
    val resp = Output(Bool())
    val data = Output(Vec(burstN, UInt(32.W)))
  })

  val single = burstN == 1
  val burstW = log2Ceil(burstN)

  val resp = RegInit(false.B)
  val data = Reg(Vec(burstN, UInt(32.W)))

  arbRead.setBurst(burstN)
  arbRead.req := io.req && !resp
  arbRead.addr := io.addr

  val offset = if (single) 0.U
  else RegInit(0.U(burstW.W))

  when(arbRead.resp) {
    data(offset) := arbRead.data
    when (arbRead.last) {
      resp := true.B
      if (!single) offset := 0.U
    } .otherwise {
      if (!single) offset := offset + 1.U
    }
  }

  when (resp) {
    resp := false.B
  }

  io.resp := resp
  io.data := data
}
