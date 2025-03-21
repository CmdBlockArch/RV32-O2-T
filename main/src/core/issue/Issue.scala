package core.issue

import chisel3._
import chisel3.util._
import conf.Conf.{prfW, robW, rsCntW, wbWidth}

class Issue[T <: Data](rsSize: Int, payload: => T) extends Module {
  import Issue._

  val dispatch = IO(Flipped(new DispatchIO(payload)))
  val out = IO(Decoupled(new OutBundle(payload)))
  val wbRd = IO(Input(Vec(wbWidth, UInt(prfW.W))))
  val io = IO(new Bundle {
    val flush = Input(Bool())
  })

  val rs = Module(new ResvStation(rsSize, payload))
  rs.dispatch :<>= dispatch
  rs.wbRd := wbRd
  rs.io.flush := io.flush

  // TODO: 读寄存器堆
}

object Issue {
  class InstBundle[T <: Data](payload: T) extends Bundle {
    val rs1 = UInt(prfW.W)
    val rs2 = UInt(prfW.W)
    val inst = payload
  }

  class DispatchBundle[T <: Data](payload: T)
    extends InstBundle(payload) {
    val rs1Ready = Bool()
    val rs2Ready = Bool()
    def ready = rs1Ready && rs2Ready
  }

  class DispatchIO[T <: Data](payload: T) extends Bundle {
    val freeCnt = Input(UInt(rsCntW.W))
    val valid = Output(Vec(2, Bool()))
    val entry = Output(Vec(2, new DispatchBundle(payload)))
  }

  class OutBundle[T <: Data](payload: T) extends Bundle {
    val src1 = UInt(32.W)
    val src2 = UInt(32.W)
    val inst = payload
  }
}
