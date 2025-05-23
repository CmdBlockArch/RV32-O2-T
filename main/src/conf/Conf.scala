package conf

import chisel3._
import chisel3.util._

object Conf {
  val debug = true
  val fastMul = true
  val resetVec = "h80000000".U(32.W)

  val fetchWidth = 2
  val decodeWidth = 2
  val renameWidth = 2
  val dispatchWidth = 2
  val commitWidth = 2

  val wbWidth = 5

  val ICache = new CacheConf(
    offsetW = 4,
    indexW = 4,
    wayN = 2
  )

  val prfN = 64 // 物理寄存器数量
  val prfW = log2Ceil(prfN) // 物理寄存器地址宽度

  val robN = 64 // ROB项数
  val robW = log2Ceil(robN) // ROB表项地址宽度

  // 各保留站项数
  val aluRsN = 8
  val bruRsN = 4
  val mduRsN = 4
  val lsuRsN = 4
  val rsCntW = log2Ceil( // 保留站项数宽度
    Seq(aluRsN, bruRsN, mduRsN, lsuRsN).max + 1)
}
