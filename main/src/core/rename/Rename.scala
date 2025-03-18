package core.rename

import chisel3._
import conf.Conf.{decodeWidth, prfN, prfW, renameWidth}
import core.decode.{Decode, InstDecoder}
import utils._

class Rename extends PiplineModule(new Decode.OutBundle, new Rename.OutBundle) {
  res.pc := cur.pc

  assert(decodeWidth == renameWidth)
  res.valid := cur.valid
  res.inst := cur.inst
  for (i <- 0 until renameWidth) {
    res.gpr(i).ard := cur.gpr(i).rd
  }

  // Register Alias Table
  val rat = RegInit(VecInit(Seq.fill(32)(0.U(prfW.W))))
  // 需要保证x0始终映射到0号物理寄存器。该物理寄存器值恒为0

  // Free List
  val freeList = RegInit(VecInit((0 until prfN).map(_.U(prfW.W))))
  val enqPtr = RegInit(0.U(prfW.W))
  val deqPtr = RegInit(1.U(prfW.W))
  val count = RegInit((prfN - 1).U(prfW.W)) // 空闲寄存器数量

  val needAlloc = VecInit((0 until renameWidth)
    .map(i => cur.valid(i) && cur.gpr(i).rd.orR)) // 需要分配物理寄存器的指令
  val allocCnt = Wire(UInt(prfW.W)) // 需要分配的物理寄存器数量
  allocCnt := needAlloc.count(_.asBool)
  setOutCond(count >= allocCnt) // 阻塞，直到有足够的空闲寄存器

  var ratBefore = WireDefault(rat)
  var deqBefore = WireDefault(deqPtr)
  for (i <- 0 until renameWidth) {
    val ratAfter = WireDefault(ratBefore)
    val deqAfter = WireDefault(deqBefore)

    res.gpr(i).rs1 := ratBefore(cur.gpr(i).rs1)
    res.gpr(i).rs2 := ratBefore(cur.gpr(i).rs2)

    when (needAlloc(i)) { // 若需要分配物理寄存器
      res.gpr(i).rd := freeList(deqBefore)
      ratAfter(cur.gpr(i).rd) := freeList(deqBefore)
      deqAfter := deqBefore + 1.U
    } .otherwise {
      res.gpr(i).rd := 0.U
    }

    ratBefore = ratAfter
    deqBefore = deqAfter
  }

  // 更新重命名数据结构
  when (update) {
    rat := ratBefore
    deqPtr := deqPtr + allocCnt
    count := count - allocCnt
  }

  // TODO: 物理寄存器释放
  // TODO: 从ROB的RAT中恢复状态
}

object Rename {
  class gprBundle extends Bundle {
    val rs1 = UInt(prfW.W)
    val rs2 = UInt(prfW.W)
    val rd = UInt(prfW.W)
    val ard = UInt(5.W)
  }

  class OutBundle extends Bundle {
    val pc = PC()
    val valid = Vec(renameWidth, Bool())
    val gpr = Vec(renameWidth, new gprBundle)
    val inst = Vec(renameWidth, new InstDecoder.DecodeBundle)
  }
}
