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

  val ICache = new CacheConf(
    offsetW = 4,
    indexW = 4,
    wayN = 2
  )

  val prfN = 64 // 物理寄存器数量
  val prfW = log2Ceil(prfN) // 物理寄存器地址宽度
}
