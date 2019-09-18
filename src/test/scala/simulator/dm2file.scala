package simulator

import breeze.linalg._
import java.io._
import simulator._

object DM2fileOnce extends App {
  var filter = DenseMatrix.fill(3, 3)(DenseMatrix.fill(3, 3)(0))
  var img = DenseMatrix.fill(3, 3)(DenseMatrix.fill(3, 3)(0))
  var filterNum = 1
  var imgNum = 1
  var nchannel = 64
  var fLen = 3
  var iLen = 34 // padding = 1
  var maxLen = 0
  var bias = 3
  filter = DenseMatrix.fill(nchannel, filterNum)(SW.randomMatrix((fLen, fLen)))
  img = DenseMatrix.fill(nchannel, imgNum)(SW.randomMatrix((iLen, iLen)))
  val filter2d = SW.fd2List(filter, 0)
  val img2d = SW.fd2List(img, 1)

  val w = new PrintWriter(new File("test.txt"))
  filter2d.foreach((l)=>{
    l.foreach(
      (num) => {
        w.write(f"${num.toByte}%02x".toUpperCase() + "\n")
      }
    )
  })

  val img2d_group = img2d.map(_.grouped(34).toList)
  for(i <- img2d_group(0).indices){
    for(j <- img2d_group.indices){
      var data35: String = ""
      for(k <- img2d_group(j)(i).indices){
        data35 = f"${img2d_group(j)(i)(k).toByte}%02x".toUpperCase() + data35
      }
      w.write(data35 + "\n")
    }
  }

  w.close()
}

object DM2file extends {
  def apply(filter2d:List[List[Int]], img2d:List[List[Int]],
            path:String="/home/SW/PRJ/eyeriss-chisel3/src/main/resources/ram.mem"): Unit ={
    val w = new PrintWriter(new File(path))
    filter2d.foreach((l)=>{
      l.foreach(
        (num) => {
          w.write(f"${num.toByte}%02x".toUpperCase() + "\n")
        }
      )
    })

    val img2d_group = img2d.map(_.grouped(34).toList)
    for(i <- img2d_group(0).indices){
      for(j <- img2d_group.indices){
        var data35: String = ""
        for(k <- img2d_group(j)(i).indices){
          data35 = f"${img2d_group(j)(i)(k).toByte}%02x".toUpperCase() + data35
        }
        w.write(data35 + "\n")
      }
    }
    w.close()
  }

}

object GenTestData{
  def apply(): (Map[String, Int], List[Int])={
    def saturationSW(x: Int): Int = {
      val tmp = if (x >= 0) {
        x / 4.0 + 0.5
      } else {
        x / 4.0 - 0.5
      }
      if (tmp >= 127) {
        127
      } else if (tmp <= -128) {
        -128
      } else {
        tmp.toInt
      }
    }

    var filter = DenseMatrix.fill(3, 3)(DenseMatrix.fill(3, 3)(0))
    var img = DenseMatrix.fill(3, 3)(DenseMatrix.fill(3, 3)(0))
    var filterNum = 1
    var imgNum = 1
    var nchannel = 64
    var fLen = 3
    var iLen = 34 // padding = 1
    var maxLen = 0
    var bias = 0
    filter = DenseMatrix.fill(nchannel, filterNum)(SW.randomMatrix((fLen, fLen)))
    img = DenseMatrix.fill(nchannel, imgNum)(SW.randomMatrix((iLen, iLen)))
    maxLen = if (filterNum * fLen * nchannel > imgNum * iLen * nchannel) {
      filterNum * fLen * nchannel
    } else {
      imgNum * iLen * nchannel
    }

    var sw1 = SW.conv4d(filter, img, true, bias=DenseMatrix.fill[Int](1, filter.cols )(bias))
    val filter2d = SW.fd2List(filter, 0)
    val img2d = SW.fd2List(img, 1)

    sw1.map((x)=>{
      print(x.toString())
      println()
    })
    println("sw: ")
    val sw = sw1.map((x) => {
      x.map((num)=>{saturationSW(num)})
    })
    sw.map((x)=>{
      print(x.toString())
      println()
    })
    //  do maxPooling
    val after_pool = sw.map(ConvTools.pooling2(_))
    println(after_pool)

    val sw1d = List[Int]().toBuffer
    val singLen = after_pool(0, 0).cols * after_pool.size
    for (k <- Range(0, after_pool(0, 0).rows)) {
      for (i <- Range(0, after_pool.cols)) {
        for (l <- Range(0, after_pool(0, 0).cols)) {
          for (j <- Range(0, after_pool.rows)) {
            sw1d.append(after_pool(j, i)(k, l))
          }
        }
      }
    }

    DM2file(filter2d, img2d)

    (Map("filterNum"->filterNum, "fLen"->fLen, "imgNum"->imgNum, "iLen"->iLen,
      "nchannel"->nchannel, "singLen"->singLen, "bias"->bias
    ), sw1d.toList)
  }
}

object app extends App{
  GenTestData()
}