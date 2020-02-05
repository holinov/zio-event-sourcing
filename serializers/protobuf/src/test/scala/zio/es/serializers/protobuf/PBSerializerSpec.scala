package zio.es.serializers.protobuf

import zio.es.serializers.protobuf.PBSerializer._
import zio.es.SerializableEvent
import zio.test.Assertion._
import zio.test._

object PBSerializerSpecs {
  private val serializationTest = test("Should serialize/deserialize `GeneratedMessage` via protobuf") {
    val item         = ZIOESSerializationTestModel("id-1", block = false)
    val ser          = implicitly[SerializableEvent[ZIOESSerializationTestModel]]
    val serBytes     = ser.toBytes(item)
    val deserialized = ser.fromBytes(serBytes)
    assert(deserialized, equalTo(item))
  }

  //noinspection TypeAnnotation
  val spec = suite("ScalaPB Serializer spec")(
    serializationTest
  )
}
object PBSerializerSpec extends DefaultRunnableSpec(PBSerializerSpecs.spec)
