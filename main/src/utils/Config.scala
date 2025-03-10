package utils

import chisel3._

object Config {
  val debug = true
  val fastMul = true
  val resetVec = "h80000000".U(32.W)

  val fetchWidth = 2 // 一周期取指数

  val ICache = new CacheConf(
    offsetW = 4,
    indexW = 4,
    wayN = 2
  )
}
