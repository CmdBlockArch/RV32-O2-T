package utils

import chisel3._

object Config {
  val debug = true
  val fastMul = true
  val resetVec = "h80000000".U(32.W)

  val ICache = new CacheConf()
}
