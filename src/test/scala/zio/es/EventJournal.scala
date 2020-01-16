package zio.es

import zio._
import zio.test._
import zio.test.Assertion._
import EventJournalHelpers._
import zio.es.EventJournal.AggregateBehaviour
import zio.es.EventJournalHelpers.TestEvent._

//noinspection TypeAnnotation
object EventJournalHelpers {

  sealed trait TestEvent

  object TestEvent {
    final case class Add(v: Float)  extends TestEvent
    final case class Subs(v: Float) extends TestEvent
    final case class Mul(v: Float)  extends TestEvent
    final case class Div(v: Float)  extends TestEvent
  }

  val CalculatingAggregate = AggregateBehaviour
    .from(0f)
    .aggregate[TestEvent](
      (s, e) =>
        ZIO.effect(e match {
          case Add(v)  => s + v
          case Subs(v) => s - v
          case Mul(v)  => s * v
          case Div(v)  => s / v
        })
    )

  val CalculatingAggregateIdentity = AggregateBehaviour[TestEvent, Float](
    0,
    (s, e) =>
      ZIO.effect(e match {
        case Add(v)  => s * 1f + v
        case Subs(v) => s * 1f - v
        case Mul(v)  => s * 1f * v
        case Div(v)  => s * 1f / v
      })
  )

  private val TestEntityName = "test1"
  val aggregateTestSuite = suite("Aggregate")(
    testM("appends events and calculates state") {
      for {
        journal <- EventJournal.inMemory[TestEvent]
        agg     <- journal.create(TestEntityName, CalculatingAggregate)
        sumState <- agg.append(Add(10)) *> agg.append(Add(20)) *> agg.append(Mul(5)) *> agg.append(Div(2)) *> agg
                     .append(Subs(3))
        sum   <- sumState.state
        state <- agg.state
      } yield assert(sum, equalTo(72)) && assert(state, equalTo(sum))
    }
  )

  val eventJournalTestSuite = suite("EventJournal")(
    testM("`append` and `load` works")(for {
      journal     <- EventJournal.inMemory[TestEvent]
      _           <- journal.persistEvent(TestEntityName, Add(10))
      _           <- journal.persistEvent(TestEntityName, Add(20))
      loaded      <- journal.load(TestEntityName, CalculatingAggregate)
      loadedState <- loaded.state
    } yield assert(loadedState, equalTo(loadedState)))
  )
}

object EventJournalSpec
    extends DefaultRunnableSpec(
      suite("Event Sourcing Specs")(
        aggregateTestSuite,
        eventJournalTestSuite
      )
    )
