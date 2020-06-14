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
    def toBytesZ(evt: E): Task[Array[Byte]]     = Task(se.toBytes(evt))
    def fromBytesZ(bytes: Array[Byte]): Task[E] = Task(se.fromBytes(bytes))
  }
}

trait EventJournal[E] { self =>

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
   * Stream of all entity ids stored
   */
  def allIds: Stream[Throwable, String]

  /**
   * Stream of all entities stored
   */
  def allEntries[S](behaviour: AggregateBehaviour[E, S]): Stream[Throwable, Aggregate[E, S]] =
    allIds.mapM(load(_, behaviour))
}

object EventJournal {

  private class InMemory[E](knownIds: Ref[Set[String]]) extends EventJournal[E] {
    private[this] val store: ConcurrentHashMap[String, Vector[E]] = new ConcurrentHashMap()

    private def getEventsFor(key: String): Task[Vector[E]] =
      Task(store.computeIfAbsent(key, _ => Vector.empty[E]))

    private def updateEventsFor(key: String, events: Vector[E]): Task[Unit] = Task(store.put(key, events)).unit

    def persistEvent(key: String, event: E): Task[Unit] =
      for {
        events <- getEventsFor(key)
        _      <- updateEventsFor(key, events :+ event)
      } yield ()

    def loadEvents(key: String): Stream[Throwable, E] = Stream.fromIteratorEffect(getEventsFor(key).map(_.toIterator))

    def allIds: Stream[Throwable, String] = Stream.unwrap(knownIds.get.map(res => Stream.fromIterable(res)))
  }

  def inMemory[E]: Task[EventJournal[E]] =
    Ref
      .make(Set.empty[String])
      .flatMap(stateRef => Task(new InMemory[E](stateRef)))

  def aggregate[E, S](initial: S)(aggregations: (S, E) => Task[S]): Task[AggregateBehaviour[E, S]] =
    ZIO.succeed(new AggregateBehaviour(initial, aggregations))
}
