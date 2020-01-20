package zio.es.storage

import java.io._
import java.nio.file._
import java.util.stream.Collectors
import scala.jdk.CollectionConverters._

import com.google.protobuf.ByteString
import scalapb._
import zio._
import zio.clock.Clock
import zio.duration._
import zio.es._
import zio.es.storage.pbfile._
import zio.stream._

//TODO: implement file storage example implementation
object FileStorage {
  type StoreEntry = EventsJournalStore.Entry
  type IndexEntry = pbfile.EventJournalsIndex.Entry
  private val BufferSize    = 3
  private val BatchMaxDelay = 500.millis

  private case class IndexData(
    entries: Map[String, EventJournalsIndex.Entry] = Map.empty.withDefault(k => EventJournalsIndex.Entry(k, 0))
  ) {
    def withJournal(entityId: String, newJournal: EventsJournalStore): UIO[IndexData] = ZIO.effectTotal {
      copy(entries = this.entries + (entityId -> EventJournalsIndex.Entry(entityId, newJournal.events.size.toLong)))
    }

    def apply(key: String): EventJournalsIndex.Entry = entries(key)
  }

  private def readFile(filePath: Path) =
    for {
      _   <- ZIO.effect(if (Files.notExists(filePath)) Files.createFile(filePath))
      res <- ZIO.effect(Files.readAllBytes(filePath))
    } yield res

  private def saveFile[T <: GeneratedMessage](file: Path, data: T): Task[Unit] =
    ZManaged
      .makeEffect(new FileOutputStream(file.toAbsolutePath.toFile)) { stream =>
        stream.flush()
        stream.close()
      }
      //      .map(new BufferedOutputStream(_))
      .use(
        out =>
          ZIO.effect(data.writeTo(out)) <*
            ZIO.effect(out.flush())
      )

  private case class WriteQueueEntry[E](entityId: String, payload: E, promise: Promise[Nothing, Unit])

  private class ProtobufFileStore[E](storePath: Path, index: RefM[IndexData], writeQueue: Queue[WriteQueueEntry[E]])(
    implicit ser: SerializableEvent[E]
  ) extends EventJournal[E] {

    private def entityFileName(entityId: String) = storePath.resolve(s"$entityId.evdata")

    private def loadEntityJournal(entityId: String): Task[EventsJournalStore] =
      readFile(entityFileName(entityId)).map(EventsJournalStore.parseFrom)

    private def saveEntityJournal(entityId: String, data: EventsJournalStore): Task[Unit] =
      saveFile(entityFileName(entityId), data)

    val processWrites: ZIO[Clock, Throwable, Unit] = {
      def updateJournal(events: List[WriteQueueEntry[E]], journal: EventsJournalStore) =
        ZIO.effect {
          val eventCount = journal.events.size
          val serialized = events.zipWithIndex.map {
            case (event, idx) =>
              EventsJournalStore.Entry((idx + eventCount).toLong, ByteString.copyFrom(ser.toBytes(event.payload)))
          }
          EventsJournalStore(journal.events ++ serialized)
        }

      def writeEntityEvents(id: String, events: List[WriteQueueEntry[E]]): Task[Unit] =
        for {
          journal    <- loadEntityJournal(id)
          newJournal <- updateJournal(events, journal)
          _          <- saveEntityJournal(id, newJournal)
          _          <- index.update(_.withJournal(id, newJournal))
          _          <- ZIO.foreachPar(events)(_.promise.succeed(()))
        } yield ()

      val storeIndex = for {
        idx <- index.get
        _   <- saveIndex(storePath, idx)
      } yield ()

      ZStream
        .fromQueue(writeQueue)
        .buffer(BufferSize)
        .aggregateAsyncWithin(Sink.collectAll[WriteQueueEntry[E]], Schedule.duration(BatchMaxDelay))
        .map(_.groupBy(_.entityId))
        .mapM(groupedBatch => ZIO.foreachPar(groupedBatch)(zz => writeEntityEvents(zz._1, zz._2)))
        .mapM(_ => storeIndex)
        .forever
        .runDrain
    }

    /**
     * Write event to journal (no loaded aggregates will be updated)
     */
    override def persistEvent(key: String, event: E): Task[Unit] =
      for {
        prom <- persistEventAsync(key, event)
        _    <- prom.await
      } yield ()

    def persistEventAsync(key: String, event: E): Task[Promise[Nothing, Unit]] =
      for {
        prom <- Promise.make[Nothing, Unit]
        _    <- writeQueue.offer(WriteQueueEntry(key, event, prom))
      } yield prom

    /**
     * Load event stream from journal
     */
    override def loadEvents(key: String): Stream[Throwable, E] = ZStream.unwrap {
      loadEntityJournal(key).map(journal => {
        Stream.fromIterable(journal.events.map(e => ser.fromBytes(e.eventBlob.toByteArray)))
      })
    }
  }

  private def indexFile(storePath: Path) = storePath.resolve("index.data")

  private def loadIndex(storePath: Path): Task[IndexData] =
    for {
      bytes <- readFile(indexFile(storePath))
      loaded <- ZIO
        .effect(EventJournalsIndex.parseFrom(bytes))
        .catchAll(_ => ZIO.succeed(EventJournalsIndex(Seq.empty)))
    } yield IndexData(loaded.index.map(v => v.entryId -> v).toMap)

  private def saveIndex(storePath: Path, index: IndexData) =
    saveFile(indexFile(storePath), EventJournalsIndex(index.entries.values.toSeq))

  private def ensureStorage(storePath: Path) =
    ZIO.effect(Files.createDirectories(storePath)).when(Files.notExists(storePath))

  def fileStore[E: SerializableEvent](storeDir: String): RIO[Clock, EventJournal[E]] = {
    val storePath = Paths.get(storeDir)
    for {
      _         <- ensureStorage(storePath)
      indexData <- loadIndex(storePath)
      index     <- RefM.make(indexData)
      queue     <- Queue.bounded[WriteQueueEntry[E]](2 * BufferSize)
      store     <- ZIO.effect(new ProtobufFileStore(storePath, index, queue))
      _         <- store.processWrites.fork
    } yield store
  }

  def destroyStore[R](storeDir: String): Task[Unit] = {
    val path = Paths.get(storeDir)
    for {
      toRem <- ZIO.effect(Files.walk(path).collect(Collectors.toList[Path]))
      _     <- ZIO.foreach(toRem.asScala.reverse)(p => ZIO.effect(Files.delete(p)))
    } yield ()
  }

  def tempFileStore[E: SerializableEvent](storeDir: String): ZManaged[Clock, Throwable, EventJournal[E]] = {
    val tmp     = Files.createTempDirectory(storeDir.replace("/", "_"))
    val tmpPath = tmp.getFileName.toFile.getAbsolutePath
    ZManaged.make(fileStore(tmpPath))(_ => destroyStore(tmpPath).refineToOrDie)
  }
}
