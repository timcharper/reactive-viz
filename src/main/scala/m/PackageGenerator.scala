package m

import java.io._
import play.api.libs.json._
import scala.collection.JavaConversions._
import scala.util.Random


object Contents {
  val normal = """
accomplishd
afar
animals
anselmo
blemishes
blockhead
bridle
colder
coldhearted
comeo
decimation
displeasure
drop
drunken
equivocate
exhale
fatwitted
franklin
gads
harvesthome
humble
illdoing
insufficience
knife
knowt
metamorphoses
neglectingly
overheardst
pierceth
quarries
repeats
rides
sheffield
shock
terribly
tribute
trident
twit
unshaken
week
widestretched
""".split("\n").toSet

  val hazardous = Set("drugs", "bodily-fluids", "organs")
  val fragile = Set("glass", "art")
  val all: Vector[String] = (fragile ++ hazardous ++ normal).toVector

  def anyContains(data: Set[String])(scan: Seq[String]): Boolean =
    scan.exists(data contains _)

  val hasHazardous = anyContains(hazardous)_
  val hasFragile = anyContains(hazardous)_
}

// object PackageGenerator extends App {
//   val lines = if (args.length > 1) args(1).toInt else 1000

//   // def readWordList = {
//   //   import java.nio.file._
//   //   val shake = FileSystems.getDefault.getPath("./shake.txt")

//   //   val wordSet = scala.collection.mutable.Set.empty[String]
//   //   for {
//   //     line <- java.nio.file.Files.readAllLines(shake)
//   //     word <- line.split(" ")
//   //     lc = notWordRegex.replaceAllIn(word.toLowerCase(), "")
//   //   } wordSet.add(lc)
//   //   wordSet.toVector
//   // }
//   // val words = readWordList

//   import Contents._
//   for (n <- 1 to 4) {
//     val f = new PrintWriter(s"packages/packages${n}.json.ld")
//     def pickRandom[T](l: Seq[T]): T = l(Math.abs(Random.nextInt % l.length))

//     def maybeObj(prob: Double)(gen: => JsObject): JsObject = {
//       if (Math.random < 0.1) gen else Json.obj()
//     }
//     for (l <- 1 to lines) {
//       val words = (1 to (Math.abs(Random.nextInt % 50))) map { _ => pickRandom(Contents.all) }

//       val box = Json.obj("contents" -> words) ++
//         maybeObj(0.10) { Json.obj("hazardous" -> anyContains(hazardous)(words)) } ++
//         maybeObj(0.10) { Json.obj("fragile" -> anyContains(fragile)(words)) }

//       f.println(box.toString)
//     }
//     f.close()
//   }
// }
