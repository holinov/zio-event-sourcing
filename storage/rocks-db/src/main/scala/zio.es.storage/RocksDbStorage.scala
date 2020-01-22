package zio.es.storage

import zio.{ Managed, Task, UIO, ZIO }
import zio.es._
import zio.rocksdb._
import zio.es.storage.rocksdb._
import zio.stream._
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Path }
import org.{ rocksdb => jrocks }

import com.google.protobuf.ByteString

import scala.jdk.CollectionConverters._

object RocksDbStorage {
  private def keyBytes(key: String) =
    ZIO.effectTotal(key.getBytes(UTF_8))

  private def loadHistory(rdb: RocksDB, key: String): Task[RocksDbEventsJournalStore] = {
    def loadHistoryInner(bytes: Option[Array[Byte]]) = bytes match {
      case None        => ZIO.effect(RocksDbEventsJournalStore.of(key, Seq.empty))
      case Some(bytes) => ZIO.effect(RocksDbEventsJournalStore.parseFrom(bytes))
    }

    for {
      keyBytes          <- keyBytes(key)
      currentStateBytes <- rdb.get(keyBytes)
      currentState      <- loadHistoryInner(currentStateBytes)
    } yield currentState
  }

  private def storeEntry[E](currentState: RocksDbEventsJournalStore, serializedEventData: Array[Byte]) =
    ZIO.succeed(RocksDbEventsJournalStore.Entry(currentState.events.length, bytesToBS(serializedEventData)))

  private def bytesToBS(bytes: Array[Byte]): ByteString = ByteString.copyFrom(bytes)

  private class RocksDBStore[E](rdb: RocksDB)(
    implicit ser: SerializableEvent[E]
  ) extends EventJournal[E] {

    /**
     * Write event to journal (no loaded aggregates will be updated)
     */
    override def persistEvent(key: String, event: E): Task[Unit] =
      for {
        currentState        <- loadHistory(rdb, key)
        serializedEventData <- ZIO.effect(ser.toBytes(event))
        serializedEvent     <- storeEntry(currentState, serializedEventData)
        updatedState        <- ZIO.effect(currentState.update(_.events.modify(_ :+ serializedEvent)))
        keyBytes            <- keyBytes(key)
        _                   <- rdb.put(keyBytes, updatedState.toByteArray)
      } yield ()

    /**
     * Load event stream from journal
     */
    override def loadEvents(key: String): Stream[Throwable, E] =
      Stream.unwrap(for {
        res    <- loadHistory(rdb, key)
        stream <- ZIO.effect(Stream.fromIterable(res.events))
      } yield stream.map(bytes => ser.fromBytes(bytes.eventBlob.toByteArray)))

  }

  private def tempDir: Managed[Throwable, Path] =
    Task(Files.createTempDirectory("zio-rocksdb")).toManaged { path =>
      UIO {
        Files
          .walk(path)
          .iterator()
          .asScala
          .toList
          .map(_.toFile)
          .sorted((o1: File, o2: File) => -o1.compareTo(o2))
          .foreach(_.delete)
      }
    }

  def openRdb[E: SerializableEvent](path: Path): Managed[Throwable, EventJournal[E]] = {
    val opts = new jrocks.Options().setCreateIfMissing(true)
    RocksDB.open(opts, path.toAbsolutePath.toString).map(new RocksDBStore[E](_))
  }

  def tmpRdb[E: SerializableEvent]: Managed[Throwable, EventJournal[E]] = tempDir.flatMap(openRdb[E])
}
