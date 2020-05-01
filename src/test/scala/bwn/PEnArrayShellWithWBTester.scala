// test for PEnArrayShell with WB
package bwn

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec, PeekPokeTester}
import config._
import node._

class PEnArrayShellWithWBTestTopTests(c: PEnArrayShellWithWBTestTop)(implicit p: Parameters) extends PeekPokeTester(c){
  val singleFilterLen = 3
  val imgNum = 1
  val relu = 1

  val filterNum = 1       //output channel
  val totalOutChannel = 1

  val nchannel = 1        //input Channel Group
  val singleImgLen = 8


  def go(filterAddr: BigInt, featureAddr: BigInt, forceOut: Int = 0): Unit ={
    // config PEConfig
    poke(c.io.peconfig.filterNum, filterNum)
    poke(c.io.peconfig.singleFilterLen, singleFilterLen)
    poke(c.io.peconfig.imgNum, imgNum)
    poke(c.io.peconfig.singleImgLen, singleImgLen)
    poke(c.io.peconfig.nchannel, nchannel)
    poke(c.io.peconfig.relu, relu)
    poke(c.io.peconfig.bias, 0)
    poke(c.io.peconfig.accState, 0)
    poke(c.io.peconfig.filterAddr, filterAddr)
    poke(c.io.peconfig.imgAddr, featureAddr)
    poke(c.io.peconfig.forceOut, forceOut)

    // stateSW 00 to 01
    poke(c.io.stateSW, 0)
    step(1)
    poke(c.io.stateSW, 1)
    step(1)
    poke(c.io.go, 1)
    step(1)
    poke(c.io.go, 0)
    poke(c.io.stateSW, 0)

    while (peek(c.io.done) != 1){
      step(1)
    }
    step(10)
  }

  var faddr = 0x00
  for(i <- 0 until 8){
    go(faddr, 0x00, 0)
    println("faddr : " + faddr.toString)
    faddr += 0x9
  }

  faddr = 0x00
  for(i <- 0 until 7){
    go(faddr, 0x08, 0)
    println("faddr : " + faddr.toString)
    faddr += 0x9
  }
  go(faddr, 0x08, 1)
  println("faddr : " + faddr.toString + "and this is end")
  //
  //  for(i <- 0 until 7){
//    go(faddr, 0x08, 0)
//    faddr += 0x9
//  }
//  go(faddr, 0x08, 1)

  step(100)

}

class PEnArrayShellWithWBTester extends ChiselFlatSpec {
  implicit val p = new SmallWidthConfig
  "running with --generate-vcd-output on" should "create a vcd file from your test" in {
    iotesters.Driver.execute(
      Array(
        "--generate-vcd-output", "on",
        "--target-dir", "test_run_dir/make_PEnArrayShellWithWBTestTop_vcd",
        "--backend-name", "verilator",
        "--top-name", "make_PEnArrayShellWithWBTestTop_vcd"
      ),
      () => new PEnArrayShellWithWBTestTop
    ) {
      c => new PEnArrayShellWithWBTestTopTests(c)
    } should be(true)
  }
}
