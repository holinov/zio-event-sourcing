package zio.es.storage

//import java.io.File

import zio._
import zio.es._
import zio.stream._

//TODO: implement file storage example implementation
object FileStorage {
  private class FileStore[E] /*(implicit ser: SerializableEvent[E])*/ extends EventJournal[E] {
//    private[this] def openHistoryFile(key: String) = ZIO.effect(new File(s"$key-history.dat"))
//    private[this] def appendToFile(file: File, evt: E) = ZIO.effect {
//      for {
//        res <- ZIO.unit
//      } yield res
//    }

    /**
     * Write event to journal (no loaded aggregates will be updated)
     */
    override def persistEvent(key: String, event: E): Task[Unit] =
      for {
        res <- ZIO.unit
      } yield res

    /**
     * Load event stream from journal
     */
    override def loadEvents(key: String): Stream[Throwable, E] = Stream.empty
  }

  def fileStore[E: SerializableEvent]: Task[EventJournal[E]] = ZIO.effect(new FileStore[E])
}
