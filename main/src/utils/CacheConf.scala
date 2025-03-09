package utils

import chisel3._
import chisel3.util._

class CacheConf(val offsetW: Int = 4, val indexW: Int = 4, val wayN: Int = 2) {
  assert(wayN >= 1)
  val wayW = log2Ceil(wayN)

  // Tag | Index | Offset
  val tagW = 32 - offsetW - indexW // tag宽度

  val setN = 1 << indexW // 组数
  val blockN = 1 << (offsetW - 2) // 每块中，32位的字的数量
  val blockW = 1 << (offsetW + 3) // 块大小，单位bit

  def getOffset(addr: UInt): UInt = {
    assert(addr.getWidth == 32)
    addr(offsetW - 1, 2)
  }

  def getIndex(addr: UInt): UInt = {
    assert(addr.getWidth == 32)
    addr(indexW + offsetW - 1, offsetW)
  }

  def getTag(addr: UInt): UInt = {
    assert(addr.getWidth == 32)
    addr(31, indexW + offsetW)
  }

  def getOffset(pc: PC): UInt = getOffset(pc.full)
  def getIndex(pc: PC): UInt = getOffset(pc.full)
  def getTag(pc: PC): UInt = getOffset(pc.full)
}
