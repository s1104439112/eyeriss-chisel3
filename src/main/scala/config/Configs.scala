package config

import axi._

case object FilterW extends Field[Int]

case object ImgW extends Field[Int]

case object BiasW extends Field[Int]

case object OSumW extends Field[Int]

case object Shape extends Field[(Int, Int)]

case object FilterSpadDepth extends Field[Int]

case object ImgSpadDepth extends Field[Int]

case object PSumMemDepth extends Field[Int]

case object RegFileW extends Field[Int]

case object RegFileDepth extends Field[Int]

case class ShellParams(
                        hostParams: AXIParams,
                        memParams: AXIParams)

case object ShellKey extends Field[ShellParams]


class DefaultConfig extends Config((site, here, up) => {
  case FilterW => 8
  case ImgW => 8
  case BiasW => 8
  case OSumW => 8

  case Shape => (3, 3)

  case FilterSpadDepth => 256
  case ImgSpadDepth => 256
  case PSumMemDepth => 16

  case RegFileW => 8
  case RegFileDepth => 8

  case ShellKey => ShellParams(
    hostParams = AXIParams(coherent = false,
      addrBits = 16,
      dataBits = 32,
      lenBits = 8,
      userBits = 1),
    memParams = AXIParams(coherent = false,
      addrBits = 64,
      dataBits = 512,
      lenBits = 8,
      userBits = 1)
  )

})