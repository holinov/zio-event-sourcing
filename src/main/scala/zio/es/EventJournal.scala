package zio.es

import java.util.concurrent.ConcurrentHashMap

import zio._
import zio.stream._
import zio.es.EventJournal.AggregateBehaviour

class Aggregate[-E, +S] private[es] (
  key: String,
  aggState: Ref[S],
  aggregations: (S, E) => Task[S],
  persist: (String, E) => Task[Unit]
) {
  def state: UIO[S] = aggState.get

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

abstract class EventJournal[E] {

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
}

object EventJournal {
  case class AggregateBehaviour[-E, S](initialState: S, aggregations: (S, E) => Task[S])

  object AggregateBehaviour {
    final case class PartialBehaviourBuilder[S](initialState: S) {
      def aggregate[E](aggregations: (S, E) => Task[S]): AggregateBehaviour[E, S] =
        AggregateBehaviour(initialState, aggregations) //TODO make non effectfull alternatives
    }

    def from[S](initialState: S): PartialBehaviourBuilder[S] = PartialBehaviourBuilder(initialState)
  }

  private class InMemory[E] extends EventJournal[E] {
    private[this] val store: ConcurrentHashMap[String, Vector[E]] = new ConcurrentHashMap()

    private def getEventsFor(key: String)                       = ZIO.effect(store.computeIfAbsent(key, _ => Vector.empty[E]))
    private def updateEventsFor(key: String, events: Vector[E]) = ZIO.effect(store.put(key, events)).unit

    def persistEvent(key: String, event: E): Task[Unit] =
      for {
        events <- getEventsFor(key)
        _      <- updateEventsFor(key, events :+ event)
      } yield ()

    def loadEvents(key: String): Stream[Throwable, E] = Stream.fromIterator(getEventsFor(key).map(_.iterator))

  }

  def inMemory[E]: Task[EventJournal[E]] = ZIO.effect(new InMemory[E])
}
