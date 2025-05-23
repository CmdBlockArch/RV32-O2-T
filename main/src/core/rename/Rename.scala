package core.rename

import chisel3._
import conf.Conf.{commitWidth, decodeWidth, prfN, prfW, renameWidth}
import core.decode.{Decode, InstDecoder}
import utils._

class Rename extends PiplineModule(new Decode.OutBundle, new Rename.OutBundle) {
  val prfFree = IO(Input(Vec(commitWidth, UInt(prfW.W))))
  val commitRat = IO(Input(Vec(32, UInt(prfW.W))))

  // ---------- 物理寄存器分配 ----------
  res.pc := cur.pc

  assert(decodeWidth == renameWidth)
  res.valid := cur.valid
  res.inst := cur.inst
  for (i <- 0 until renameWidth) {
    res.gpr(i).ard := cur.gpr(i).rd
  }

  // Register Alias Table
  val rat = RegInit(VecInit(Seq.fill(32)(0.U(prfW.W))))
  assert(rat(0.U) === 0.U) // 需要保证x0始终映射到0号物理寄存器。该物理寄存器值恒为0

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
  when (out.fire) {
    rat := ratBefore
    deqPtr := deqPtr + allocCnt
  }

  // ---------- 物理寄存器释放 ----------
  val prfFreeR = RegNext(prfFree, 0.U.asTypeOf(prfFree))

  val freeValid = VecInit(prfFreeR.map(_.orR))
  val freeCnt = freeValid.count(_.asBool)
  enqPtr := enqPtr + freeCnt

  assert(commitWidth == 2)
  when (freeValid.reduce(_ || _)) {
    freeList(enqPtr) := Mux(freeValid(0), prfFreeR(0), prfFreeR(1))
  }
  when (freeValid.reduce(_ && _)) { // freeCnt === 2.U
    freeList(enqPtr + 1.U) := prfFreeR(1)
  }

  when (out.fire) {
    count := (count + freeCnt) - allocCnt
  } .otherwise {
    count := count + freeCnt
  }

  // ---------- 分支预测失败恢复 ----------
  val commitRatAllocCnt = commitRat.count(_.orR)
  when (RegNext(flush, false.B)) {
    rat := commitRat
    deqPtr := enqPtr + commitRatAllocCnt + 1.U
    count := (prfN - 1).U - commitRatAllocCnt
  }

  /* 出于时序考虑，将物理寄存器释放和分支预测失败恢复延后一个周期。
  分支预测失败后，整个前端都会全部冲刷，后端更年轻的全部指令也都会冲刷，所以正确性不会受影响。
  这里确定状态的RAT没有用寄存器缓存，这是因为冲刷后commitRAT不会马上更新
  直到重新取指的下一条指令提交时，commitRAT才有可能会被更新，所以无需缓存 */
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
