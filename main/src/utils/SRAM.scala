package utils

import chisel3._
import chisel3.util._
import conf.Conf.debug

class SRAM[T <: Data](val size: Int, val dataType: T) extends Module {
  val addrW = log2Ceil(size)
  assert(addrW > 0)

  val io = IO(new Bundle {
    val en = Input(Bool())
    val we = Input(Bool())
    val addr = Input(UInt(addrW.W))
    val din = Input(dataType)
    val dout = Output(dataType)
  })

  when (io.en) {
    assert(io.addr < size.U)
  }

  val mem = Reg(Vec(size, dataType))

  when (io.en && io.we) {
    mem(io.addr) := io.din
  }

  if (debug) {
    io.dout := Mux(io.en && !io.we, mem(io.addr), 0.U.asTypeOf(dataType))
  } else {
    io.dout := mem(io.addr)
  }
}
