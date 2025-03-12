package core.decode

import chisel3._
import chisel3.util.BitPat
import chisel3.util.experimental.decode._

object OpcodeRawStr {
  val LUI    = "0110111"
  val AUIPC  = "0010111"
  val JAL    = "1101111"
  val JALR   = "1100111"
  val BRANCH = "1100011"
  val LOAD   = "0000011"
  val STORE  = "0100011"
  val CALRI  = "0010011"
  val CALRR  = "0110011"
  val SYSTEM = "1110011"
  val FENCE  = "0001111"
  val ATOMIC = "0101111"
}

import OpcodeRawStr._

object Rs1EnField extends BoolDecodeField[InstPattern] {
  override def name = "rs1En" // 是否读取rs1
  override def genTable(op: InstPattern): BitPat = {
    op.opcode.rawString match {
      case LUI | AUIPC | JAL => n
      case JALR | BRANCH | LOAD | STORE | CALRI | CALRR => y
      case SYSTEM => op.func3.rawString match {
        case "000" => n
        case _ => op.func3(2).rawString match { // zicsr
          case "0" => y // csrr
          case "1" => n // csri
        }
      }
      case ATOMIC => y
      case _ => n
    }
  }
}

object Rs2EnField extends BoolDecodeField[InstPattern] {
  override def name = "rs2En" // 是否有读取rs2
  override def genTable(op: InstPattern): BitPat = {
    op.opcode.rawString match {
      case LUI | AUIPC | JAL | JALR | LOAD | CALRI | SYSTEM => n
      case BRANCH | STORE | CALRR => y
      case ATOMIC => op.func5.rawString match {
        case "00010" => n // lr
        case _ => y // sc amo
      }
      case _ => n
    }
  }
}

object RdEnField extends BoolDecodeField[InstPattern] {
  override def name = "rdEn" // 是否有寄存器写入
  override def genTable(op: InstPattern): BitPat = {
    op.opcode.rawString match {
      case LUI | AUIPC | JAL | JALR | LOAD | CALRI | CALRR | ATOMIC => y
      case BRANCH | STORE => n
      case SYSTEM => op.func3.rawString match {
        case "000" => dc // rd位均为0
        case _ => y // zicsr
      }
      case _ => n
    }
  }
}

object ImmSelField extends DecodeField[InstPattern, UInt] {
  override def name = "immSel"
  override def chiselType = UInt(6.W)
  override def genTable(op: InstPattern): BitPat = {
    op.opcode.rawString match {
      case JALR | LOAD | CALRI => BitPat("b000001") // immI
      case STORE               => BitPat("b000010") // immS
      case BRANCH              => BitPat("b000100") // immB
      case LUI | AUIPC         => BitPat("b001000") // immU
      case JAL                 => BitPat("b010000") // immJ
      case SYSTEM => op.func3.rawString match {
        case "000" => dc
        case _ => BitPat("b100000") // Zicsr, immZ
      }
      case _ => dc
    }
  }
}

object InstField {
  val fields = Seq(
    Rs1EnField,
    Rs2EnField,
    RdEnField,
    ImmSelField
  )
}

