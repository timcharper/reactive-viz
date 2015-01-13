package m.lib

import java.io.{InputStream, FileInputStream, File}

class AutoClosingLineIterator(filename: File) extends scala.collection.Iterator[String] {
  private var stream: InputStream = null
  private lazy val i = synchronized {
    println(s"Open $filename")
    stream = new FileInputStream(filename)
    scala.io.Source.fromInputStream(stream).getLines.toIterator
  }

  def hasNext = {
    val n = i.hasNext
    if (! n) stream.close
    n
  }

  def next = i.next
}
