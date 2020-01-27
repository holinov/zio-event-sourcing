package zio.es.storage

import java.nio.ByteBuffer
import java.util.UUID

import zio._
import zio.es._
import zio.stream._
import com.datastax.driver.core._
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.utils.UUIDs

import scala.collection.convert.AsScalaConverters
import scala.collection.convert.AsJavaConverters

object CassandraStorage extends AsScalaConverters with AsJavaConverters {

  type ManagedCluster = Managed[Throwable, Cluster]
  type ManagedSession = Managed[Throwable, Session]

  implicit class StoreSessionOps(val session: Session) extends AnyVal { // TODO: do all operations on blocking pool or using `executeAsync`
    def execute(cql: String, values: Map[String, AnyRef]): Task[ResultSet] = ZIO.effect(session.execute(cql, values))
    def execute(cql: String, values: Any*): Task[ResultSet]                = ZIO.effect(session.execute(cql, values: _*))
    def execute(cql: String): Task[ResultSet]                              = ZIO.effect(session.execute(cql))
    def execute(stmt: Statement): Task[ResultSet]                          = ZIO.effect(session.execute(stmt))

    def useKeyspace(ks: String): Task[Unit]  = execute(s"USE $ks").unit
    def dropKeyspace(ks: String): Task[Unit] = execute(s"DROP KEYSPACE $ks").unit
    def ensureKeyspace(ks: String, replicationFactor: Int): Task[Unit] =
      execute(
        s"""CREATE KEYSPACE IF NOT EXISTS $ks WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : $replicationFactor }""".stripMargin
      ).unit

    def ensureStorageTables(ks: String, tableName: String): Task[Unit] =
      execute(s"""CREATE TABLE IF NOT EXISTS $ks.$tableName (
                 | entityId text,
                 | eventId timeuuid,
                 | payload blob,
                 | PRIMARY KEY (entityId, eventId)
                 |)""".stripMargin).unit

    def pepareStmt(cql: String): Task[PreparedStatement]            = ZIO.effect(session.prepare(cql))
    def pepareStmt(stmt: RegularStatement): Task[PreparedStatement] = ZIO.effect(session.prepare(stmt))
  }

  class CassandraEventJournal[E](cSession: Session, table: String)(implicit ser: SerializableEvent[E])
      extends EventJournal[E] {
    private val session: StoreSessionOps = cSession

    private def insertEvent(key: String, e: E) = {
      val timeUuid: Task[UUID] = ZIO.effectTotal(UUIDs.timeBased())

      def insertStmt(bytes: Array[Byte]): Task[RegularStatement] =
        for {
          eventId <- timeUuid
          stmt <- ZIO.effect(
            QueryBuilder
              .insertInto(table)
              .value("entityId", key)
              .value("eventId", eventId)
              .value("payload", ByteBuffer.wrap(bytes))
          )
        } yield stmt

      for {
        payload <- ser.toBytesZ(e)
        stmt    <- insertStmt(payload)
        res     <- session.execute(stmt)
      } yield res
    }

    /**
     * Write event to journal (no loaded aggregates will be updated)
     */
    override def persistEvent(key: String, event: E): Task[Unit] = insertEvent(key, event).unit

    /**
     * Load event stream from journal
     */
    override def loadEvents(key: String): Stream[Throwable, E] =
      Stream.unwrap {
        val selectStmt = ZIO.effect(QueryBuilder.select("entityId", "eventId", "payload").from(table).where {
          QueryBuilder.eq("entityId", key)
        })

        for {
          stmt <- selectStmt
          resI = session.execute(stmt).map(rr => rr.iterator()).orDie
          res  = Stream.fromIterator[Throwable, Row](resI.map(it => asScala(it)))
        } yield res
      }.map(row => ser.fromBytes(row.getBytes("payload").array()))
  }

  /**
   * Manages connection to cassandra.
   * Ensures kayspace and table
   * Connection is closed after managed is free
   */
  def connect[E: SerializableEvent](
    servers: Seq[String],
    keyspace: String,
    table: String,
    replicationFactor: Int = 3,
    destroyAfterStop: Boolean = false
  ): Managed[Throwable, CassandraEventJournal[E]] = {
    val startSession = for {
      cluster <- ZIO.effect(Cluster.builder().addContactPoints(servers: _*).build())
      session <- ZIO.effect(cluster.connect())
      _       <- session.ensureKeyspace(keyspace, replicationFactor)
      _       <- session.useKeyspace(keyspace)
      _       <- session.ensureStorageTables(keyspace, table)
    } yield session
    ZManaged
      .make(startSession)(s => s.dropKeyspace(keyspace).orDie.when(destroyAfterStop) *> ZIO.effectTotal(s.close()))
      .map(new CassandraEventJournal(_, table))
  }

  /**
   * Does not manages connection. Session is never closed
   */
  def attach[E: SerializableEvent](session: Session, table: String): UIO[CassandraEventJournal[E]] =
    UIO(new CassandraEventJournal(session, table))
}
