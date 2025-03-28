package utils

import chisel3._

object Debug {
  private val debug = conf.Conf.debug

  def DebugRegNext[T <: Data](next: T, init: T) = {
    if (debug) Some(RegNext(next, init)) else None
  }
  def DebugRegNext[T <: Data](next: T) = {
    if (debug) Some(RegNext(next)) else None
  }
  def DebugRegNext[T <: Data](next: Option[T]) = {
    if (debug) Some(RegNext(next.get)) else None
  }
  def DebugOutput[T <: Data](source: => T) = {
    if (debug) Some(Output(source)) else None
  }
  def DebugIO[T <: Data](iodef: => T) = {
    if (debug) Some(IO(iodef)) else None
  }
}
