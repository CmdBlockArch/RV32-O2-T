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

object ExuSelField extends DecodeField[InstPattern, UInt] {
  override def name = "exuSel"
  override def chiselType = UInt(4.W)
  override def genTable(op: InstPattern): BitPat = {
    op.opcode.rawString match {
      case LUI | AUIPC | JAL | JALR | BRANCH => BitPat("b0010") // BRU
      case LOAD | STORE => BitPat("b1000") // LSU
      case CALRI => BitPat("b0001") // ALU
      case CALRR => op.func7.rawString match {
        case "0000001" => BitPat("b0100") // MDU
        case _ => BitPat("b0001") // ALU
      }
      case _ => dc
    }
  }
}

object AluSignEnField extends BoolDecodeField[InstPattern] {
  override def name = "aluSignEn" // 是否为有符号操作
  override def genTable(op: InstPattern): BitPat = {
    op.opcode.rawString match {
      case CALRI => op.func3.rawString match {
        case "101" => y // srai
        case "001" => dc // slli, srli
        case _ => n
      }
      case CALRR => op.func3.rawString match {
        case "101" | "000" => y // sra, sub
        case _ => dc
      }
      case _ => dc
    }
  }
}

object BruRdSelField extends DecodeField[InstPattern, UInt] {
  override def name = "bruRdSel"
  override def chiselType = UInt(2.W)
  override def genTable(op: InstPattern): BitPat = {
    op.opcode.rawString match {
      case LUI        => BitPat("b01") // 0 + imm
      case AUIPC      => BitPat("b11") // pc + imm
      case JAL | JALR => BitPat("b10") // pc + 4
      case BRANCH     => BitPat("b00") // none
      case _ => dc
    }
  }
}

object JalrField extends BoolDecodeField[InstPattern] {
  override def name = "jalr"
  override def genTable(op: InstPattern): BitPat = {
    if (InstPattern.jalr.bitPat.cover(op.inst)) y else n
  }
}

object MemField extends DecodeField[InstPattern, UInt] {
  override def name = "mem" // 访存操作编码
  override def chiselType = UInt(4.W)
  override def genTable(op: InstPattern): BitPat = {
    op.opcode.rawString match {
      case LOAD => op.func3.rawString match {
        case "000" => BitPat("b1100") // lb
        case "001" => BitPat("b1101") // lh
        case "010" => BitPat("b1110") // lw
        case "100" => BitPat("b1000") // lbu
        case "101" => BitPat("b1001") // lhu
        case _ => dc
      }
      case STORE  => op.func3.rawString match {
        case "000" => BitPat("b0100") // sb
        case "001" => BitPat("b0101") // sh
        case "010" => BitPat("b0110") // sw
        case _ => dc
      }
      case ATOMIC => op.func5.rawString match {
        case "00010" => BitPat("b0001") // lr
        case "00011" => BitPat("b0010") // sc
        case _       => BitPat("b0011") // amo
      }
      case _ => BitPat("b0000") // 0
    }
  }
}

object EbreakField extends BoolDecodeField[InstPattern] {
  override def name = "ebreak" // 是否为EBREAK操作
  override def genTable(op: InstPattern): BitPat = {
    if (InstPattern.ebreak.bitPat.cover(op.inst)) y else n
  }
}

object InstField {
  val fields = Seq(
    Rs1EnField, Rs2EnField, RdEnField,
    ImmSelField, ExuSelField,
    AluSignEnField,
    BruRdSelField, JalrField,
    MemField,
    EbreakField,
  )
}

