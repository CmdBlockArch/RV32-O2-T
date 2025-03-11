package conf

import chisel3._

object Conf {
  val debug = true
  val fastMul = true
  val resetVec = "h80000000".U(32.W)

  val fetchWidth = 2
  val decodeWidth = 2

  val ICache = new CacheConf(
    offsetW = 4,
    indexW = 4,
    wayN = 2
  )
}
