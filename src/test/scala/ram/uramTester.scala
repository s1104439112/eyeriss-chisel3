// test for uram
package ram

import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import config._

class URAMTests(c: Avalue2MaxChannel)(implicit p: Parameters) extends PeekPokeTester(c){
  val writeNum = (1 << p(URAMKey).addrW) * p(MaxChannel)
  for(i <- 0 until writeNum){
    poke(c.io.op, 1)
    poke(c.io.waddr, i)
    poke(c.io.wdata, i / (1 << p(URAMKey).addrW))
    step(1)
    poke(c.io.op, 0)
  }
  step(1)
  for(i <- 0 until (1 << p(URAMKey).addrW)){
    poke(c.io.raddr, i)
    step(1)
  }
}

class URAMTester extends ChiselFlatSpec {
  implicit val p = new SmallWidthConfig
  "running with --generate-vcd-output on" should "create a vcd file from your test" in {
    iotesters.Driver.execute(
      Array(
        "--generate-vcd-output", "on",
        "--target-dir", "test_run_dir/make_uram_vcd",
        "--top-name", "make_uram_vcd",
        "--backend-name", "verilator"
      ),
      () => new Avalue2MaxChannel
    ) {
      c => new URAMTests(c)
    } should be(true)
  }
}
