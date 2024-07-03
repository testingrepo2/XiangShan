/***************************************************************************************
* Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
* Copyright (c) 2020-2021 Peng Cheng Laboratory
*
* XiangShan is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/
package xiangshan.mem

import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util._
import utils._
import utility._
import xiangshan._

class MemUnit(params: MemUnitParams)(implicit p: Parameters) extends LazyModule with HasXSParameter {
  lazy val module: MemUnitImp = params.memType match {
    case AtomicUnit() => new AtomicUnitImp(this)
    case LoadUnit() => new LoadUnitImp(this)
    case StoreUnit() => new StoreUnitImp(this)
    case HybridUnit() => new HybridUnitImp(this)
    case _ => null
  }
}

class MemUnitIO()(implicit p: Parameters, params: MemUnitParams) extends XSBundle {
  // Inputs
  val redirect = Flipped(ValidIO(new Redirect))
  val hartId = OptionWrapper(params.isAtomicUnit, Input(UInt(hartIdLen.W)))
  val csrCtrl = Flipped(new CustomCSRCtrlIO)

  val fromIssuePath = new Bundle() {
    val intIn = OptionWrapper(params.hasIntPort, Flipped(Decoupled(new MemExuInput)))
    val vecIn = OptionWrapper(params.hasVecPort, Flipped(Decoupled(new VecPipeBundle)))
  }
  val fromDataPath = new Bundle() {
    val tlb = new TlbRespIO(2)
    val pmp = Flipped(new PMPRespBundle)
    val dcache = new DCacheResp //
    val lsq = OptionWrapper(params.hasStoreForward, new LsqForwardResp)
    val sbuffer = OptionWrapper(params.hasStoreForward, new SbufferForwardResp)
    val dataBus = OptionWrapper(params.hasBusForward, new DcacheToLduForwardIO)
    val mshr = OptionWrapper(params.hasMSHRForward, new LduToMissqueueForwardResp)
  }

  // Outputs
  val toDataPath = new Bundle() {
    val tlb = new TlbReqIO(2)
    val dcache = new DcacheResp
    val lsq = OptionWrapper(params.hasStoreForward, new LsqForwardReq)
    val sbuffer = OptionWrapper(params.hasStoreForward, new SbufferForwardResp)
    val mshr = OptionWrapper(params.hasMSHRForward, new LduToMissqueueForwardReq)
  }
  val toIssuePath = new Bundle() {
    val feedback = OptionWrapper(params.hasFeedback, ValidIO(new RSFeedback))
    val intOut = OptionWrapper(params.hasIntPort, Decoupled(new MemExuOutput))
    val vecOut = OptionWrapper(params.hasVecPort, Decoupled(new VecPipelineFeedbackIO(isVStore = false)))
  }
  val debugLsInfo = OptionWrapper(params.hasDebugInfo, Output(new DebugLsInfoBundle))
  val lsTopdownInfo = OptionWrapper(params.hasTopDownInfo, Output(new LsTopdownInfo))
}

class MemUnitImp(override val wrapper: MemUnit)(implicit p: Parameters, val params: MemUnitParams)
  extends LazyModuleImp(wrapper)
  with HasXSParameter {
  val io = IO(new MemUnitIO(params))

  val fromIssuePath = params.fromIssuePath
  val fromDataPath = params.fromDataPath
}

class AtomicUnitImp(override val wrapper: MemUnit)(implicit p: Parameters, params: MemUnitParams)
  extends MemUnitImp(wrapper)
{
  override lazy val io = IO(new MemUnitIO(params))

  //-------------------------------------------------------
  // Atomics Memory Accsess FSM
  //-------------------------------------------------------
  val sInvalid :: sFlushReq :: sPmpCheck :: sWaitFlushResp :: sCacheReq :: sCacheResp :: sCacheRespLatch :: sFinish :: Nil = Enum(8)
  val state = RegInit(sInvalid)
  val outValid = RegInit(false.B)
  val dataValid = RegInit(false.B)

  val in = Reg(new MemExuInput)
  val exceptionVec = RegInit(0.U.asTypeOf(ExceptionVec))
  val atomOverrideXtval = RegInit(false.B)
  val haveSentFirstTlbReq = RegInit(false.B)
  val isLr = in.uop.fuOpType === LSUOpType.lr_w || in.uop.fuOpType === LSUOpType.lr_d

  // paddr after translation
  val paddr = Reg(UInt())
  val gpaddr = Reg(UInt())
  val vaddr = in.src(0)
  val isMmio = Reg(Bool())

  // dcache response data
  val respData = Reg(UInt())
  val respDataWire = WireInit(0.U)
  val isLrscValid = Reg(Bool())
  val sbufferEmpty = false.B
}

class LoadUnitImp(override val wrapper: MemUnit)(implicit p: Parameters, params: MemUnitParams)
  extends MemUnitImp(wrapper)
{
  override lazy val io = IO(new MemUnitIO(params))
}

class StoreUnitImp(override val wrapper: MemUnit)(implicit p: Parameters, params: MemUnitParams)
  extends MemUnitImp(wrapper)
{
  override lazy val io = IO(new MemUnitIO(params))
}

class HybridUnitImp(override val wrapper: MemUnit)(implicit p: Parameters, params: MemUnitParams)
  extends MemUnitImp(wrapper)
{
  override lazy val io = IO(new MemUnitIO(params))
}