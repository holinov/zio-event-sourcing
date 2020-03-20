package zio.es

import java.util.concurrent.ConcurrentHashMap

import zio._
import zio.stream._

class Aggregate[-E, +S] private[es] (
  key: String,
  aggState: Ref[S],
  aggregations: (S, E) => Task[S],
  persist: (String, E) => Task[Unit]
) {
  val state: UIO[S] = aggState.get

  def appendAll(evt: Iterable[E]): Task[Aggregate[E, S]] =
    ZIO.foreach(evt)(append).map(_.head)

  def append(evt: E): Task[Aggregate[E, S]] =
    for {
      _ <- persist(key, evt)
      _ <- appendNoPersist(evt)
    } yield this

  private[es] def appendNoPersist(evt: E): Task[Aggregate[E, S]] =
    for {
      curState <- state
      modified <- aggregations(curState, evt)
      _        <- aggState.set(modified)
    } yield this

}

trait SerializableEvent[E] extends Any with Serializable {
  def toBytes(evt: E): Array[Byte]
  def fromBytes(bytes: Array[Byte]): E
}

object SerializableEvent {
  implicit class SerializableEventOps[E](se: SerializableEvent[E]) {
    def toBytesZ(evt: E): Task[Array[Byte]]     = ZIO.effect(se.toBytes(evt))
    def fromBytesZ(bytes: Array[Byte]): Task[E] = ZIO.effect(se.fromBytes(bytes))
  }
}

trait ISO[A, B] {
  def toB(a: A): B
  def toA(b: B): A
}
object ISO {
  def apply[A, B](implicit iso: ISO[A, B]): ISO[A, B] = iso
  def apply[A, B](_toB: A => B, _toA: B => A): ISO[A, B] = new ISO[A, B] {
    override def toB(a: A): B = _toB(a)
    override def toA(b: B): A = _toA(b)
  }
}

trait EventJournal[E] { ej =>

  /**
   * Write event to journal (no loaded aggregates will be updated)
   */
  def persistEvent(key: String, event: E): Task[Unit]

  /**
   * Load event stream from journal
   */
  def loadEvents(key: String): Stream[Throwable, E]

  /**
   * Create new empty aggregate
   */
  def create[S](key: String, behaviour: AggregateBehaviour[E, S]): Task[Aggregate[E, S]] =
    for {
      initialStateRef <- Ref.make(behaviour.initialState)
    } yield new Aggregate[E, S](key, initialStateRef, behaviour.aggregations, persistEvent)

  /**
   * Load aggregate from event journal
   */
  def load[S](key: String, behaviour: AggregateBehaviour[E, S]): Task[Aggregate[E, S]] =
    for {
      agg <- create[S](key, behaviour)
      res <- loadEvents(key).foldM(agg)(_ appendNoPersist _)
    } yield res

  /**
   * Allows bi-directional EventJournal[E] <=> EventJournal[E1] transformations
   */
  def bimap[E1](implicit iso: ISO[E, E1]): EventJournal[E1] = new EventJournal[E1] {
    override def persistEvent(key: String, event: E1): Task[Unit] = ej.persistEvent(key, iso.toA(event))
    override def loadEvents(key: String): Stream[Throwable, E1]   = ej.loadEvents(key).map(iso.toB)
  }

  /**
   * Allows bi-directional EventJournal[E] <=> EventJournal[E1] transformations
   */
  def bimap[E1](f1: E => E1, f2: E1 => E): EventJournal[E1] = bimap(ISO(f1, f2))
}

object EventJournal {

  private class InMemory[E] extends EventJournal[E] {
    private[this] val store: ConcurrentHashMap[String, Vector[E]] = new ConcurrentHashMap()

    private def getEventsFor(key: String): Task[Vector[E]] =
      ZIO.effect(store.computeIfAbsent(key, _ => Vector.empty[E]))
    private def updateEventsFor(key: String, events: Vector[E]): Task[Unit] = ZIO.effect(store.put(key, events)).unit

    def persistEvent(key: String, event: E): Task[Unit] =
      for {
        events <- getEventsFor(key)
        _      <- updateEventsFor(key, events :+ event)
      } yield ()

    def loadEvents(key: String): Stream[Throwable, E] = Stream.fromIterator(getEventsFor(key).map(_.iterator))

  }

  def inMemory[E]: Task[EventJournal[E]] = ZIO.effect(new InMemory[E])
  def aggregate[E, S](initial: S)(aggregations: (S, E) => Task[S]): Task[AggregateBehaviour[E, S]] =
    ZIO.succeed(new AggregateBehaviour(initial, aggregations))
}
