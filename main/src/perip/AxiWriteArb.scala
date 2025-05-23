package perip

import chisel3._

class AxiWriteIO extends Bundle {
  // aw
  val awready = Input(Bool())
  val awvalid = Output(Bool())
  val awaddr  = Output(UInt(32.W))
  val awid    = Output(UInt(4.W))
  val awlen   = Output(UInt(8.W))
  val awsize  = Output(UInt(3.W))
  val awburst = Output(UInt(2.W))
  // w
  val wready = Input(Bool())
  val wvalid = Output(Bool())
  val wdata  = Output(UInt(32.W))
  val wstrb  = Output(UInt(4.W))
  val wlast  = Output(Bool())
  // b
  val bready = Output(Bool())
  val bvalid = Input(Bool())
  val bresp  = Input(UInt(2.W))
  val bid    = Input(UInt(4.W))
}

class AxiWriteArb(masterN: Int) extends Module {
  import AxiWriteArb._

  val master = IO(Vec(masterN, Flipped(new WriteIO)))
  val slave = IO(new AxiWriteIO)

  // 当前是否存在待接收的请求
  val doReq = WireDefault(master.foldLeft(false.B)((res, m) => res || m.req))
  // 若有请求待接收，应该是哪个id（id大优先级更高）
  val doReqId = Wire(UInt(4.W))
  doReqId := (0 until masterN).foldLeft(0.U)((res, i) => Mux(master(i).req, i.U, res))

  val running = RegInit(false.B) // 是否正在进行请求
  val curId = Reg(UInt(4.W)) // 当前请求id
  val awvalid = RegInit(false.B) // 向slave转发请求的awvalid
  val curMaster = master(curId)
  when (!running && doReq) { // 可以接受请求
    running := true.B
    awvalid := true.B
    curId := doReqId
  }

  // 向slave转发请求
  slave.awvalid := awvalid
  when (slave.awready && slave.awvalid) {
    awvalid := false.B
  }
  slave.awaddr := curMaster.addr
  slave.awid := curId
  slave.awlen := curMaster.len
  slave.awsize := curMaster.size
  slave.awburst := curMaster.burst

  // 始终准备好接收b通道返回
  slave.bready := true.B
  when (slave.bvalid) {
    running := false.B
  }

  for (i <- 0 until masterN) {
    // 转发b通道返回给每个master
    master(i).resp := slave.bvalid && slave.bid === i.U
    master(i).err := slave.bresp.orR

    master(i).dataReady := slave.wready && curId === i.U
  }

  // 数据通道
  slave.wvalid := curMaster.dataValid
  slave.wdata := curMaster.data
  slave.wstrb := curMaster.strb
  slave.wlast := curMaster.last
}

object AxiWriteArb {
  class WriteIO extends Bundle {
    val req = Output(Bool())
    val addr = Output(UInt(32.W))
    val size = Output(UInt(2.W))
    val burst = Output(UInt(2.W))
    val len = Output(UInt(8.W))

    val dataReady = Input(Bool())
    val dataValid = Output(Bool())
    val data = Output(UInt(32.W))
    val strb = Output(UInt(4.W))
    val last = Output(Bool())

    val resp = Input(Bool())
    val err = Input(Bool())
  }
}
