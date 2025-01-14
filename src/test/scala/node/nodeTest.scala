package node

import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import breeze.linalg._
import java.io.File

import simulator._

object getVerilog extends App {
  println("generate node verilog")
  //  chisel3.Driver.execute(Array("--target-dir", "test_run_dir"), () => new Node(true, (1,1), 16))
  chisel3.Driver.execute(Array("--target-dir", "test_run_dir"), () => new PEArray((6, 7)))
}

class PEArrayTest(c: PEArray, /*filter:DenseMatrix[DenseMatrix[Int]],img:DenseMatrix[DenseMatrix[Int]],*/ var loop: Int)
  extends PeekPokeTester(c) {
  do {
    var filter = DenseMatrix.fill(3, 3)(DenseMatrix.fill(3, 3)(0))
    var img = DenseMatrix.fill(3, 3)(DenseMatrix.fill(3, 3)(0))
    var filterNum = 1
    var imgNum = 1
    var nchannel = 1
    var maxLen = 0
    var fLen = 0
    var iLen = 0
    do {
      val random = scala.util.Random
      nchannel = random.nextInt(1) + 3
      filterNum = random.nextInt(1) + 3
      imgNum = random.nextInt(1) + 2
      fLen = random.nextInt(1) + 3
      iLen = random.nextInt(1) + 5
      filter = DenseMatrix.fill(nchannel, filterNum)(SW.randomMatrix((fLen, fLen)))
      img = DenseMatrix.fill(nchannel, imgNum)(SW.randomMatrix((iLen, iLen)))
      maxLen = if (filterNum * fLen * nchannel > imgNum * iLen * nchannel) {
        filterNum * fLen * nchannel
      } else {
        imgNum * iLen * nchannel
      }
    } while (maxLen > 255 | iLen > 7 + fLen - 1 | fLen > 6)

    //    val filterNum = filter.cols
    //    val fLen = filter(0, 0).cols
    //    val imgNum = img.cols
    //    val iLen = img(0, 0).cols
    //    val nchannel = filter.rows

    val sw = SW.conv4d(filter, img, true)
    val filter2d = SW.fd2List(filter, 0)
    val img2d = SW.fd2List(img, 1)
    println(s"filterNum: ${filterNum}")
    println(s"imgNum: ${imgNum}")
    println(s"channel: ${nchannel}")
    println(s"fLen: ${fLen}")
    println(s"iLen: ${iLen}")
    filter2d.map((x) => {
      println(x.toString())
      println()
    })
    println("img2d: ")
    img2d.map((x) => {
      println(x.toString())
      println()
    })
    println("sw: ")
    sw.map((x) => {
      println(x.toString())
      println()
    })

    val sw1d = List[Int]().toBuffer
    val singLen = sw(0, 0).cols * sw.size
    for (k <- Range(0, sw(0, 0).rows)) {
      for (i <- Range(0, sw.cols)) {
        for (l <- Range(0, sw(0, 0).cols)) {
          for (j <- Range(0, sw.rows)) {
            sw1d.append(sw(j, i)(k, l))
          }
        }
      }
    }
    //    println(sw1d.toString())


    poke(c.io.stateSW, 0)
    step(1) // because PE buf state, so need 1 clock

    // second send basic infotmation to PE, include filterNum, singleFilterLen, imgNum, singleImgLen, nchannel
    poke(c.io.peconfig.filterNum, filterNum)
    poke(c.io.peconfig.singleFilterLen, fLen)
    poke(c.io.peconfig.imgNum, imgNum)
    poke(c.io.peconfig.singleImgLen, iLen)
    poke(c.io.peconfig.nchannel, nchannel)
    poke(c.io.peconfig.relu, 1)
    step(1) // PE buf basic infotmation after 1 clock

    // third put data in
    poke(c.io.dataIn.valid, 0)
    for (i <- filter2d.indices) {
      for (j <- filter2d(0).indices) {
        poke(c.io.dataIn.valid, 1)
        poke(c.io.dataIn.bits.data(0), filter2d(i)(j))
        poke(c.io.dataIn.bits.dataType, 0)
        poke(c.io.dataIn.bits.positon.row, i)
        poke(c.io.dataIn.bits.positon.col, -1)
        poke(c.io.dataIn.bits.cnt, 1)
        step(1)
      }
    }
    for (i <- img2d.indices) {
      for (j <- img2d(0).indices) {
        poke(c.io.dataIn.valid, 1)
        poke(c.io.dataIn.bits.data(0), img2d(i)(j))
        poke(c.io.dataIn.bits.dataType, 1)
        poke(c.io.dataIn.bits.positon.row, i)
        poke(c.io.dataIn.bits.positon.col,  1) //because cols 0  is  row controller
        poke(c.io.dataIn.bits.cnt, 1)
        step(1)
      }
    }
    //    for (i <- filter2d.indices) { // row = filter's row
    //      for (j <- Range(0, iLen - fLen + 1)) { // col = iLen - fLen + 1
    //        for (k <- img2d(0).indices) {
    //          poke(c.io.dataIn.valid, 1)
    //          poke(c.io.dataIn.bits.data, img2d(i + j)(k))
    //          poke(c.io.dataIn.bits.dataType, 1)
    //          poke(c.io.dataIn.bits.positon.row, i)
    //          poke(c.io.dataIn.bits.positon.col, j + 1) //because cols 0  is  row controller
    //          step(1)
    //        }
    //      }
    //    }
    poke(c.io.dataIn.valid, 0)
    step(1)
    // fourth let PE in getdata state
    poke(c.io.stateSW, 1)
    //    println(s"filter2d(0) len : ${filter2d(0).length}")
    step(filter2d(0).length + 1)

    // finial let PE in cal state
    poke(c.io.stateSW, 2)
    step(1)
    //    c.io.oSumMEM.foreach((x) => {
    //      poke(x.ready, 1)
    //    })
    c.io.oSumSRAM.foreach((x) => {
      poke(x.ready, 1)
    })
    var error = 0
    var jj = List.fill(c.io.oSumSRAM.length)(0).toBuffer
    while (peek(c.io.done) == 0) {
      for (i <- c.io.oSumSRAM.indices) {
        if (peek(c.io.oSumSRAM(i).valid) == 1) {
          expect(c.io.oSumSRAM(i).bits, sw1d(i * singLen + jj(i)))
          //          expect(c.io.oSumMEM(i).bits, sw1d(i * singLen + jj(i)))
          // println(peek(c.io.oSum(i).bits).toString())
          if (peek(c.io.oSumSRAM(i).bits) != sw1d(i * singLen + jj(i))) {
            error += 1
          }
          jj(i) += 1
        }
      }
      step(1)
    }
    step(1)
    println(s"jj reduce: ${jj.reduce(_ + _)}")
    println(s"sw1d: ${sw1d.length}")
    assert(jj.reduce(_ + _) == sw1d.length)
    reset(50)
    loop -= 1
    println(s"loop: ${loop + 1}")
    println(s"===============ERROR: ${error}======================")
  } while (loop > 0)

}

// aim to 5x7
class MNISTTest(c: PEArray) extends PeekPokeTester(c) {

  var totalcnt = 0
  var conv1cnt = 0
  var conv2cnt = 0
  var errorcnt = 0

  val PEArrayRow = c.shape._1
  val PEArrayCol = c.shape._2
  val pic = Data.pics
  val flt1W = Data.flts1
  val flt2W = Data.flts2
  val fc1W = Data.fc1
  val fc2W = Data.fc2
  val fc3W = Data.fc3

  def logicMapFltW(flts: DenseMatrix[DenseMatrix[Int]]): DenseMatrix[DenseMatrix[Int]] = {
    flts
  }

  def logicMapImg(imgs: DenseMatrix[DenseMatrix[Int]]): DenseMatrix[DenseMatrix[Int]] = {
    imgs
  }

  def phyMapFltW(flts: DenseMatrix[DenseMatrix[Int]]): DenseMatrix[DenseMatrix[Int]] = {
    assert(flts.rows < 6)
    flts
  }

  def phyMapImgW(img: DenseMatrix[DenseMatrix[Int]], filterRow: Int):
  (List[DenseMatrix[DenseMatrix[Int]]], List[Range]) = {
    var buf = List[DenseMatrix[DenseMatrix[Int]]]().toBuffer
    var time = 0
    val step = PEArrayCol
    val maxImgRow = filterRow + PEArrayCol - 1
    var flag = true
    val sliceStrategy = List[Range]().toBuffer
    while (flag) {
      if (img(0, 0).rows <= time * step + maxImgRow) {
        buf.append(img.map(_ (time * step until img(0, 0).rows, ::)))
        sliceStrategy.append(time * step until img(0, 0).rows - filterRow + 1)
        println(s"time: ${time} from ${time * step} until ${img(0, 0).rows}")
        flag = false
      } else {
        buf.append(img.map(_ (time * step until time * step + maxImgRow, ::)))
        sliceStrategy.append(time * step until (time + 1) * step)
        println(s"time: ${time} from ${time * step} until ${time * step + maxImgRow}")
      }
      time += 1
    }
    (buf.toList, sliceStrategy.toList)
  }

  def conv(filter: DenseMatrix[DenseMatrix[Int]], img: DenseMatrix[DenseMatrix[Int]], depthwise: Boolean = false):
  DenseMatrix[DenseMatrix[Int]] = {
    val filterNum = filter.cols
    val imgNum = img.cols
    val nchannel = filter.rows
    assert(filter.rows == img.rows)
    val fLen = filter(0, 0).cols
    val iLen = img(0, 0).cols

    val sw = SW.conv4d(filter, img, true, depthwise)
    val filter2d = SW.fd2List(filter, 0)
    val img2d = SW.fd2List(img, 1)
    println(s"filterNum: ${filterNum}")
    println(s"imgNum: ${imgNum}")
    println(s"channel: ${nchannel}")
    println(s"fLen: ${fLen}")
    println(s"iLen: ${iLen}")
    filter2d.map((x) => {
      println(x.toString())
      println()
    })
    println("img2d: ")
    img2d.map((x) => {
      println(x.toString())
      println()
    })
    println("sw: ")
    sw.map((x) => {
      println(x.toString());
      println()
    })

    val sw1d = List[Int]().toBuffer
    val singLen = sw(0, 0).cols * sw.size
    for (k <- Range(0, sw(0, 0).rows)) {
      for (i <- Range(0, sw.cols)) {
        for (l <- Range(0, sw(0, 0).cols)) {
          for (j <- Range(0, sw.rows)) {
            sw1d.append(sw(j, i)(k, l))
          }
        }
      }
    }
    //    println(sw1d.toString())


    poke(c.io.stateSW, 0)
    step(1) // because PE buf state, so need 1 clock

    // second send basic infotmation to PE, include filterNum, singleFilterLen, imgNum, singleImgLen, nchannel
    poke(c.io.peconfig.filterNum, filterNum)
    poke(c.io.peconfig.singleFilterLen, fLen)
    poke(c.io.peconfig.imgNum, imgNum)
    poke(c.io.peconfig.singleImgLen, iLen)
    poke(c.io.peconfig.nchannel, nchannel)
    poke(c.io.peconfig.relu, 1)
    step(1) // PE buf basic infotmation after 1 clock

    // third put data in
    poke(c.io.dataIn.valid, 0)
    for (i <- filter2d.indices) {
      for (j <- filter2d(0).indices) {
        poke(c.io.dataIn.valid, 1)
//        poke(c.io.dataIn.bits.data, filter2d(i)(j))
        poke(c.io.dataIn.bits.dataType, 0)
        poke(c.io.dataIn.bits.positon.row, i)
        poke(c.io.dataIn.bits.positon.col, -1)
        step(1)
      }
    }
    for (i <- filter2d.indices) { // row = filter's row
      for (j <- Range(0, img2d.length - filter2d.length + 1)) { // col = iLen - fLen + 1
        for (k <- img2d(0).indices) {
          poke(c.io.dataIn.valid, 1)
//          poke(c.io.dataIn.bits.data, img2d(i + j)(k))
          poke(c.io.dataIn.bits.dataType, 1)
          poke(c.io.dataIn.bits.positon.row, i)
          poke(c.io.dataIn.bits.positon.col, j + 1) //because cols 0  is  row controller
          step(1)
        }
      }
    }
    poke(c.io.dataIn.valid, 0)
    step(1)
    // fourth let PE in getdata state
    poke(c.io.stateSW, 1)
    //    println(s"filter2d(0) len : ${filter2d(0).length}")
    step(filter2d(0).length + 1)

    // finial let PE in cal state
    poke(c.io.stateSW, 2)
    step(1)
    //    c.io.oSumMEM.foreach((x) => {
    //      poke(x.ready, 1)
    //    })
    c.io.oSumSRAM.foreach((x) => {
      poke(x.ready, 1)
    })
    var error = 0
    var jj = List.fill(c.io.oSumSRAM.length)(0).toBuffer
    while (peek(c.io.done) == 0) {
      for (i <- c.io.oSumSRAM.indices) {
        if (peek(c.io.oSumSRAM(i).valid) == 1) {
          expect(c.io.oSumSRAM(i).bits, sw1d(i * singLen + jj(i)))
          totalcnt += 1
          //          expect(c.io.oSumMEM(i).bits, sw1d(i * singLen + jj(i)))
          //           println(peek(c.io.oSumSRAM(i).bits).toString() + s"<${i * singLen + jj(i)}>" + sw1d(i * singLen + jj(i)).toString)
          if (peek(c.io.oSumSRAM(i).bits) != sw1d(i * singLen + jj(i))) {
            error += 1
            errorcnt += 1
          }
          jj(i) += 1
        }
      }
      step(1)
    }
    step(1)
    println(s"jj reduce: ${jj.reduce(_ + _)}")
    println(s"sw1d: ${sw1d.length}")
    assert(jj.reduce(_ + _) == sw1d.length)
    reset(1)
    println(s"===============ERROR: ${error}======================")
    sw
  }

  def depthwiseConv(filter: DenseMatrix[DenseMatrix[Int]], img: DenseMatrix[DenseMatrix[Int]]):
  DenseMatrix[DenseMatrix[Int]] = {
    var result = DenseMatrix.fill(filter.cols, img.cols)(DenseMatrix.fill(3, 3)(0))
    for (x <- Range(0, filter.cols)) {
      for (y <- Range(0, img.cols)) {
        result(x, y) = (filter(::, x).toArray, img(::, y).toArray).zipped.map((f, i) => {
          conv(DenseMatrix.fill(1, 1)(f), DenseMatrix.fill(1, 1)(i), true)(0, 0)
          // SW.conv2d(_, _, true)
        }).reduce(_ + _)
      }
    }
    result
  }

  val F1 = DenseMatrix.fill(1, 6)(DenseMatrix.fill(5, 5)(0))
  for (x <- Range(0, flt1W.length)) {
    F1(0, x) = flt1W(x)
  }
  val F2 = DenseMatrix.fill(6, 16)(DenseMatrix.fill(5, 5)(0))
  for (x <- Range(0, F2.cols)) {
    for (y <- Range(0, F2.rows)) {
      F2(y, x) = flt2W(x)
    }
  }
  var img = DenseMatrix.fill(1, 1)(DenseMatrix.fill(32, 32)(0))
  img(0, 0) = pic
  var (imgs, sliceStrategy) = phyMapImgW(logicMapImg(img), F1(0, 0).rows)
  println(F1(0, 0).toString())
  var conv1Out = DenseMatrix.fill(F1.cols, img.cols)(DenseMatrix.fill(img(0, 0).rows - F1(0, 0).rows + 1,
    img(0, 0).cols - F1(0, 0).cols + 1)(0))
  for (x <- imgs.indices) {
    val temp = conv(F1, imgs(x))
    for (i <- sliceStrategy(x)) {
      for (j <- Range(0, conv1Out(0, 0).cols)) {
        for (k <- Range(0, conv1Out.rows)) {
          for (l <- Range(0, conv1Out.cols)) {
            conv1Out(k, l)(i, j) = temp(k, l)(i - sliceStrategy(x).toList.head, j)
          }
        }
      }
    }
  }
  conv1cnt = totalcnt
  conv1Out = conv1Out.map(ConvTools.pooling(_))
  println(conv1Out(0, 0).toString())
  var (conv1Outs, sliceStrategy2) = phyMapImgW(logicMapImg(conv1Out), F2(0, 0).rows)
  var conv2Out = DenseMatrix.fill(F2.cols, conv1Out.cols)(DenseMatrix.fill(conv1Out(0, 0).rows - F2(0, 0).rows + 1,
    conv1Out(0, 0).cols - F2(0, 0).cols + 1)(0))
  for (x <- conv1Outs.indices) {
    val temp = depthwiseConv(F2, conv1Outs(x))
    for (i <- sliceStrategy2(x)) {
      for (j <- Range(0, conv2Out(0, 0).cols)) {
        for (k <- Range(0, conv2Out.rows)) {
          for (l <- Range(0, conv2Out.cols)) {
            conv2Out(k, l)(i, j) = temp(k, l)(i - sliceStrategy2(x).toList.head, j)
          }
        }
      }
    }
  }
  conv2cnt = totalcnt - conv1cnt
  conv2Out = conv2Out.map(ConvTools.pooling(_))
  var fc1 = DenseMatrix(conv2Out.toArray.toList.map(_.t.flatten().toArray.toList).reduce(_ ::: _)) * fc1W
  fc1 = fc1.map(_ / 255)
  var fc2 = fc1 * fc2W
  var fc3 = fc2 * fc3W
  println(argmax(fc3).toString())
  println("total cnt: " + totalcnt.toString)
  println("error cnt: " + errorcnt.toString)
  println("conv1 cnt: " + conv1cnt.toString)
  println("conv2 cnt: " + conv2cnt.toString)
}

class PEArrayTester extends ChiselFlatSpec {
  "running with --generate-vcd-output on" should "create a vcd file from your test" in {
    iotesters.Driver.execute(
      Array("--generate-vcd-output", "on", "--target-dir", "test_run_dir/make_PEArray_vcd", "--top-name", "make_PEArray_vcd",
        "--backend-name", "verilator"),
      () => new PEArray((6, 7), 8)
    ) {
      c => new PEArrayTest(c, 100)
    } should be(true)
    //    new File("test_run_dir/make_PEArray_vcd/PEArray.vcd").exists should be(true)

  }
}


class MNISTTester extends ChiselFlatSpec {
  "running with --generate-vcd-output on" should "create a vcd file from your test" in {
    iotesters.Driver.execute(
      Array("--generate-vcd-output", "on", "--target-dir", "test_run_dir/make_MNIST_vcd", "--top-name", "make_MNIST_vcd",
        "--backend-name", "verilator"),
      () => new PEArray((5, 7))
    ) {
      c => new MNISTTest(c)
    } should be(true)
    //    new File("test_run_dir/make_PEArray_vcd/PEArray.vcd").exists should be(true)
  }
}
