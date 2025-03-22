package core.decode

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.DecodeTable

class InstDecoder extends Module {
  import InstDecoder._

  val inst = IO(Input(UInt(32.W)))
  val gpr = IO(Output(new GprBundle))
  val res = IO(Output(new DecodeBundle))

  // 译码控制信号
  val decodeTable = new DecodeTable(InstPattern.patterns, InstField.fields)
  val table = decodeTable.table
  val cs = decodeTable.decode(inst)

  // GPR读写
  gpr.rs1 := Mux(cs(Rs1EnField), inst(19, 15), 0.U)
  gpr.rs2 := Mux(cs(Rs2EnField), inst(24, 20), 0.U)
  gpr.rd := Mux(cs(RdEnField), inst(11, 7), 0.U)

  // 立即数
  val immI = Cat(Fill(20, inst(31)), inst(31, 20))
  val immS = Cat(Fill(20, inst(31)), inst(31, 25), inst(11, 7))
  val immB = Cat(Fill(20, inst(31)), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W))
  val immU = Cat(inst(31, 12), 0.U(12.W))
  val immJ = Cat(Fill(12, inst(31)), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W))
  val immZ = Cat(0.U(27.W), inst(19, 15))
  res.imm := Mux1H(cs(ImmSelField), Seq(immI, immS, immB, immU, immJ, immZ))

  // 执行单元选择
  res.exu := cs(ExuSelField)

  // 异常
  res.err := cs(EbreakField)

  // 功能码
  res.func := inst(14, 12)

  // ALU
  res.aluUseImm := !inst(5)
  res.aluSign := cs(AluSignEnField) && inst(30)

  // BRU
  res.bruRdSel := cs(BruRdSelField)
  res.bruJalr := cs(JalrField)

  // LSU
  res.lsuMem := cs(MemField)
}

object InstDecoder {
  class GprBundle extends Bundle {
    val rs1 = UInt(5.W)
    val rs2 = UInt(5.W)
    val rd = UInt(5.W)
  }

  class DecodeBundle extends Bundle {
    val imm = UInt(32.W)
    val func = UInt(3.W)
    val err = Bool()
    val exu = UInt(4.W)

    val aluUseImm = Bool()
    val aluSign = Bool()

    val bruRdSel = UInt(2.W)
    val bruJalr = Bool()

    val lsuMem = UInt(4.W)
  }
}
