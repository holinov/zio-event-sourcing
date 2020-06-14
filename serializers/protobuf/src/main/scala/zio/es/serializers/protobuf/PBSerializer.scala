package zio.es.serializers.protobuf

import scalapb._
import zio.es.SerializableEvent

object PBSerializer {
  implicit def serializer[T <: GeneratedMessage](implicit
    companion: GeneratedMessageCompanion[T]
  ): SerializableEvent[T] =
    new SerializableEvent[T] {
      override def toBytes(evt: T): Array[Byte] = evt.toByteArray

      override def fromBytes(bytes: Array[Byte]): T = companion.parseFrom(bytes)
    }
}
