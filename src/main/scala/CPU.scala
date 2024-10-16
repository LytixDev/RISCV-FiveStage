package FiveStage

import barriers.{EXMEM, IDEX, IFID, MEMWB}
import chisel3._
import chisel3.core.Input
import chisel3.experimental.MultiIOModule
import chisel3.experimental._
import stages.{Execute, InstructionDecode, InstructionFetch, MemoryFetch}


class CPU extends MultiIOModule {

  val testHarness = IO(
    new Bundle {
      val setupSignals = Input(new SetupSignals)
      val testReadouts = Output(new TestReadouts)
      val regUpdates   = Output(new RegisterUpdates)
      val memUpdates   = Output(new MemUpdates)
      val currentPC    = Output(UInt(32.W))
    }
  )

  /**
    You need to create the classes for these yourself
    */
  val IFID  = Module(new IFID).io
  val IDEX  = Module(new IDEX).io
  val EXMEM  = Module(new EXMEM).io
  val MEMWB = Module(new MEMWB).io

  val IF  = Module(new InstructionFetch)
  val ID  = Module(new InstructionDecode)
  val EX  = Module(new Execute)
  val MEM = Module(new MemoryFetch)
  // val WB  = Module(new Execute) (You may not need this one?)

  /**
    * Setup. You should not change this code
    */
  IF.testHarness.IMEMsetup     := testHarness.setupSignals.IMEMsignals
  ID.testHarness.registerSetup := testHarness.setupSignals.registerSignals
  MEM.testHarness.DMEMsetup    := testHarness.setupSignals.DMEMsignals

  testHarness.testReadouts.registerRead := ID.testHarness.registerPeek
  testHarness.testReadouts.DMEMread     := MEM.testHarness.DMEMpeek

  /**
    spying stuff
    */
  testHarness.regUpdates := ID.testHarness.testUpdates
  testHarness.memUpdates := MEM.testHarness.testUpdates
  testHarness.currentPC  := IF.testHarness.PC


  /**
    TODO: Your code here
    */
  // IFID Barrier
  IFID.PCIn := IF.io.PC
  IFID.instructionIn := IF.io.instruction
  // Instruction Decode Stage
  ID.io.instruction := IFID.instructionOut
  // IDEX Barrier
  IDEX.instructionIn := IFID.instructionOut
  IDEX.PCIn := IFID.PCOut
  IDEX.dataAIn := ID.io.dataA
  IDEX.dataBIn := ID.io.dataB
  IDEX.immIn := ID.io.imm
  IDEX.controlSignalsIn := ID.io.controlSignals
  IDEX.ALUopIn := ID.io.ALUop
  IDEX.branchTypeIn := ID.io.branchType
  IDEX.op1SelectIn := ID.io.op1Select
  IDEX.op2SelectIn := ID.io.op2Select
  // Execute Stage
  EX.io.op1Select := IDEX.op1SelectOut
  EX.io.op2Select := IDEX.op2SelectOut
  EX.io.PC := IDEX.PCOut
  EX.io.dataA := IDEX.dataAOut
  EX.io.dataB := IDEX.dataBOut
  EX.io.imm := IDEX.immOut
  EX.io.controlSignals := IDEX.controlSignalsOut
  EX.io.ALUop := IDEX.ALUopOut
  EX.io.branchType := IDEX.branchTypeOut
  // EXMEM Barrier
  EXMEM.PCIn := IDEX.PCOut
  EXMEM.instructionIn := IDEX.instructionOut
  EXMEM.dataBIn := IDEX.dataBOut
  EXMEM.controlSignalsIn := IDEX.controlSignalsOut
  EXMEM.dataAluIn := EX.io.aluResult
  EXMEM.branchTakenIn := EX.io.branchTaken
  // Instruction Fetch Extra
  // For unconditional jumps and branches that are taken, signal to the IF to use the incoming newPC
  IF.io.useNewPCControl := EXMEM.controlSignalsOut.jump || EXMEM.branchTakenOut
  IF.io.newPC := EXMEM.dataAluOut
  // Memory Stage
  MEM.io.dataAddress := EXMEM.dataAluOut
  MEM.io.writeEnable := EXMEM.controlSignalsOut.memWrite
  MEM.io.dataIn := EXMEM.dataAluOut
  when (EXMEM.controlSignalsOut.memWrite) {
    MEM.io.dataIn := EXMEM.dataBOut
  }
  // MEMWB Barrier
  MEMWB.memReadIn := MEM.io.dataOut // What we read from memory
  MEMWB.instructionIn := EXMEM.instructionOut
  MEMWB.controlSignalsIn := EXMEM.controlSignalsOut
  MEMWB.dataAluIn := EXMEM.dataAluOut
  // For jump instructions, the alu result is used to update the PC, while the
  // data we actually want to write to the given register is the old PC + 4.
  when (EXMEM.controlSignalsOut.jump) {
    MEMWB.dataAluIn := EXMEM.PCOut + 4.U
  }
  // Instruction Decode extra from MEMWB Barrier
  ID.io.writeAddress := MEMWB.instructionOut.registerRd
  ID.io.writeData := MEMWB.dataAluOut
  // For read instructions we write the data we read
  when (MEMWB.controlSignalsOut.memRead) {
    ID.io.writeData := MEMWB.memReadOut
  }
  // Register write
  ID.io.writeEnable := MEMWB.controlSignalsOut.regWrite
}
