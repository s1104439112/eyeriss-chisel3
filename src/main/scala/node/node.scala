package node

import chisel3._
import chisel3.util._

class Positon(val w: Int) extends Bundle {
  val row = SInt(w.W)
  val col = SInt(w.W)
}

class dataPackage(val w: Int) extends Bundle {
  val data = SInt(w.W)
  val dataType = UInt(1.W)
  val positon = new Positon(4)
}

class Node(row: Boolean, positon: (Int, Int), w: Int) extends Module {
  val io = IO(new Bundle {
    val dataPackageIn = Flipped(DecoupledIO((new dataPackage(w)).cloneType))
    val dataPackageOut = DecoupledIO((new dataPackage(w)).cloneType)
  })

  val qIn = Wire(io.dataPackageIn.cloneType)
  val q = Queue(qIn, 5)

  qIn <> io.dataPackageIn
  q <> io.dataPackageOut

  val boardcast = Wire(Bool())

  if (row) {
    boardcast := (io.dataPackageIn.bits.positon.row - (-1).asSInt(4.W)) === 0.S
    io.dataPackageIn.ready := qIn.ready &
      ( boardcast | (io.dataPackageIn.bits.positon.row === positon._1.S))
    qIn.valid := io.dataPackageIn.valid &
      ((io.dataPackageIn.bits.positon.row === (-1).S) | (io.dataPackageIn.bits.positon.row === positon._1.S))
  } else {
    boardcast := (io.dataPackageIn.bits.positon.col - (-1).asSInt(4.W)) === 0.S
    io.dataPackageIn.ready := qIn.ready &
      ( boardcast | (io.dataPackageIn.bits.positon.col === positon._2.S))
    qIn.valid := io.dataPackageIn.valid &
      ( boardcast | (io.dataPackageIn.bits.positon.col === positon._2.S))
  }

}
