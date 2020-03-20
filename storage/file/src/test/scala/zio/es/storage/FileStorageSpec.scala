package zio.es.storage

import java.util.UUID

import zio.Has
import zio.es._
import zio.es.serializers.protobuf._
import zio.test._
import zio.test.Assertion._

//noinspection TypeAnnotation
object FileStorageSpec extends DefaultRunnableSpec {
  implicit val serializer: SerializableEvent[JournalTestModel] = PBSerializer.serializer[JournalTestModel]
  type FileStore = Has[EventJournal[JournalTestModel]]

  private val serializationTestZ = test("Should find implicit serializer for event") {
    val item         = JournalTestModel("id-1", block = false)
    val ser          = implicitly[SerializableEvent[JournalTestModel]]
    val serBytes     = ser.toBytes(item)
    val deserialized = ser.fromBytes(serBytes)
    assert(deserialized)(equalTo(item))
  }

  private val storePath = "testStoreData/store"

  /*
  private def buildTestAggregate: Task[AggregateBehaviour[JournalTestModel, Seq[JournalTestModel]]] =
    EventJournal.aggregate[JournalTestModel, Seq[JournalTestModel]](Seq.empty[JournalTestModel]) {
      case (s, e) => ZIO.effect(s :+ e)
    }

  private val createEmptyZ = testM("creates empty Aggregate") {
    for {
      store         <- ZIO.environment[EventJournal[JournalTestModel]]
      testAggregate <- buildTestAggregate
      created       <- store.create(UUID.randomUUID().toString, testAggregate)
      createdState  <- created.state
    } yield assert(createdState)(isEmpty)
  }

  private val saveAndLoadZ = testM("saves and loads Aggregate") {
    val eventsSeq = Seq(
      JournalTestModel("id-1", block = false),
      JournalTestModel("id-2", block = true),
      JournalTestModel("id-3", block = true)
    )

    val entityId = UUID.randomUUID().toString
    tmpTestStore.use { store =>
      for {
        testAggregate <- buildTestAggregate
        aggregate     <- store.load[Seq[JournalTestModel]](entityId, testAggregate)
        _             <- aggregate.appendAll(eventsSeq)
        createdState  <- aggregate.state
        loaded        <- store.load(entityId, testAggregate)
        loadedState   <- loaded.state
      } yield assert(loadedState)(equalTo(eventsSeq)) && assert(createdState)(equalTo(eventsSeq))
    }
  }

  private val timeoutDuration = 5.second
   */

  val tmpTestStore = FileStorage.tempFileStore(storePath + UUID.randomUUID())

  override def spec: ZSpec[Environment, Failure] =
    suite("FileStorage specs")(serializationTestZ)

  //    suite("FileStorage specs")(
  //      serializationTestZ @@ timeout(timeoutDuration),
  //      createEmptyZ @@ timeout(timeoutDuration),
  //      saveAndLoadZ @@ timeout(timeoutDuration)
  //    )
}
