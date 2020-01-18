package zio.es.storage

import zio._
import zio.es._
import zio.stream._

//TODO: implement file storage example implementation
object FileStorage {
  private class FileStore[E] extends EventJournal[E] {

    /**
     * Write event to journal (no loaded aggregates will be updated)
     */
    override def persistEvent(key: String, event: E): Task[Unit] = Task.unit

    /**
     * Load event stream from journal
     */
    override def loadEvents(key: String): Stream[Throwable, E] = Stream.empty
  }

  def fileStore[E]: Task[EventJournal[E]] = ZIO.effect(new FileStore[E])
}
