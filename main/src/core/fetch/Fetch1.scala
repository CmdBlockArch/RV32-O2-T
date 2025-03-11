package core.fetch

import chisel3._
import chisel3.util._
import cache.{CacheData, CacheMeta}
import conf.Conf.fetchWidth
import conf.Conf.ICache._
import perip.{AxiReadArb, MemReader}

class Fetch1 extends Module {
  val in = IO(Flipped(new Fetch0.OutBundle))
  val out = IO(Decoupled(new Fetch.OutBundle))
  val dataRead = IO(new CacheData.ReadIO)
  val metaWrite = IO(new CacheMeta.WriteIO)
  val dataWrite = IO(new CacheData.WriteIO)
  val arbRead = IO(new AxiReadArb.ReadIO)
  val io = IO(new Bundle {
    val ready = Output(Bool())
    val flush = Input(Bool())
  })

  val memReader = Module(new MemReader(blockN))
  arbRead :<>= memReader.arbRead

  // 流水级寄存器
  val valid = RegInit(false.B)
  val cur = Reg(new Fetch0.OutBundle)
  val req = RegInit(false.B)
  when (io.flush) {
    valid := false.B
  } .elsewhen (io.ready) {
    valid := true.B
    cur := in
  }
  io.ready := !req && (!valid || out.fire)

  // 读ICache
  val tag = getTag(cur.pc)
  val index = getIndex(cur.pc)
  val offset = getOffset(cur.pc)
  dataRead.en := valid && cur.hit
  dataRead.index := getIndex(cur.pc)
  dataRead.way := cur.hitWay

  // 输出指令读取结果
  assert(fetchWidth == 2)
  out.valid := valid && cur.hit
  out.bits.pc := cur.pc
  out.bits.valid(0) := true.B
  out.bits.inst(0) := dataRead.data(offset)
  out.bits.valid(1) := offset =/= (blockN - 1).U
  out.bits.inst(1) := dataRead.data(offset + 1.U)

  // 二路组相联的PLRU
  assert(wayN == 2)
  val plru = Reg(Vec(setN, UInt(1.W)))
  when (valid && cur.hit) {
    plru(index) := cur.hitWay
  }
  val evictWay = ~plru(index)

  // ICache缺失回填
  memReader.io.req := req
  memReader.io.addr := Cat(tag, index, 0.U(offsetW.W))
  when (valid && !cur.hit) {
    req := true.B
  }
  when (memReader.io.resp) {
    req := false.B
    when (valid) { // 未发生flush
      cur.hit := true.B
      cur.hitWay := evictWay
    }
  }
  metaWrite.en := memReader.io.resp
  metaWrite.index := index
  metaWrite.way := evictWay
  metaWrite.data.tag := tag
  dataWrite.en := memReader.io.resp
  dataWrite.index := index
  dataWrite.way := evictWay
  dataWrite.data := memReader.io.data
}
