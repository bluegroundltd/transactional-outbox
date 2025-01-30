package io.github.bluegroundltd.springoutbox

import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.springoutbox.postgreslock.PostgresLockDao
import io.github.bluegroundltd.springoutbox.utils.DummyOutboxHandler
import io.github.bluegroundltd.springoutbox.utils.UnitTestSpecification

import java.time.Clock

class SpringTransactionalOutboxBuilderSpec extends UnitTestSpecification {

  Clock clock
  PostgresLockDao postgresLockDao
  OutboxStore outboxStore
  InstantOutboxPublisher instantOutboxPublisher
  SpringTransactionalOutboxBuilder springTransactionalOutboxBuilder

  def setup() {
    clock = Mock()
    postgresLockDao = Mock()
    outboxStore = Mock()
    instantOutboxPublisher = Mock()
    springTransactionalOutboxBuilder = new SpringTransactionalOutboxBuilder(postgresLockDao, outboxStore, instantOutboxPublisher)
  }

  def "should inject dependencies automatically when using spring builder"() {
    given:
      def handlers = Set.of(new DummyOutboxHandler())
      def monitorLocksIdentifier = generateLong()
      def cleanupLocksIdentifier = generateLong()

    when:
      def outboxBuilder = springTransactionalOutboxBuilder
        .withClock(clock)
        .withHandlers(handlers)
        .withMonitorLocksIdentifier(monitorLocksIdentifier)
        .withCleanupLocksIdentifier(cleanupLocksIdentifier)
        .build()

    then:
      outboxBuilder.clock == clock
      for (handler in handlers) {
        outboxBuilder.outboxHandlers[handler.supportedType] == handler
      }
      outboxBuilder.monitorLocksProvider.postgresLockDao instanceof PostgresLockDao
      outboxBuilder.monitorLocksProvider.id == monitorLocksIdentifier
      outboxBuilder.cleanupLocksProvider.postgresLockDao instanceof PostgresLockDao
      outboxBuilder.cleanupLocksProvider.id == cleanupLocksIdentifier
      outboxBuilder.outboxStore == outboxStore
      outboxBuilder.instantOutboxPublisher == instantOutboxPublisher
  }
}
