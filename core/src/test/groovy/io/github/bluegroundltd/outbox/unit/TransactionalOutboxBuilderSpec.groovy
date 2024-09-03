package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxBuilder
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.processing.OutboxItemProcessorDecorator
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.utils.DummyOutboxHandler
import io.github.bluegroundltd.outbox.utils.UnitTestSpecification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class TransactionalOutboxBuilderSpec extends UnitTestSpecification {
  def clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  def monitorLocksProvider = GroovyMock(OutboxLocksProvider)
  def cleanupLocksProvider = GroovyMock(OutboxLocksProvider)
  def store = GroovyMock(OutboxStore)
  def instantOutboxPublisher = GroovyMock(InstantOutboxPublisher)

  def "Should build a transactional outbox instance #testCase"() {
    given:
      def builder = TransactionalOutboxBuilder.make(clock)

    and:
      def handlerA = new DummyOutboxHandler(clock)
      def handlerB = GroovyMock(OutboxHandler)
      def mockedBType = GroovyMock(OutboxType)
      def handlers = Set.of(handlerA, handlerB)
      def expectedOutboxTypes = Set.of(handlerA.getSupportedType(), mockedBType)
      def decorator = GroovyMock(OutboxItemProcessorDecorator)

    when:
      def transactionalOutboxBuilder = builder
        .withHandlers(handlers)
        .withMonitorLocksProvider(monitorLocksProvider)
        .withCleanupLocksProvider(cleanupLocksProvider)
        .withStore(store)
        .withInstantOutboxPublisher(instantOutboxPublisher)
        .withInstantOrderingEnabled(generateBoolean())
        .withThreadPriority(generateIntNonZero(10))
        .addProcessorDecorator(decorator)

    and:
      TransactionalOutbox transactionalOutbox
      if (withCustomThreadPoolSize && withCustomThreadPoolTimeOut) {
        transactionalOutbox = transactionalOutboxBuilder.withThreadPoolSize(5).withThreadPoolTimeOut(Duration.ofSeconds(5)).build()
      } else if (withCustomThreadPoolSize && !withCustomThreadPoolTimeOut) {
        transactionalOutbox = transactionalOutboxBuilder.withThreadPoolSize(5).build()
      } else if (!withCustomThreadPoolSize && withCustomThreadPoolTimeOut) {
        transactionalOutbox = transactionalOutboxBuilder.withThreadPoolTimeOut(Duration.ofSeconds(5)).build()
      } else {
        transactionalOutbox = transactionalOutboxBuilder.build()
      }

    and:
      def mappedHandlers = builder.handlers as Map<OutboxType, OutboxHandler>

    then:
      transactionalOutbox instanceof TransactionalOutboxImpl
      2 * handlerB.getSupportedType() >> mockedBType
      mappedHandlers.size() == 2
      mappedHandlers.keySet() == expectedOutboxTypes

    where:
      testCase                                                        | withCustomThreadPoolSize | withCustomThreadPoolTimeOut
      "with default thread pool size and default thread pool timeout" | false                    | false
      "with custom thread pool size and custom thread pool timeout"   | true                     | true
      "with default thread pool size and custom thread pool timeout"  | false                    | true
      "with custom thread pool size and default thread pool timeout"  | true                     | false
  }

  def "Should throw if handlers with same supporting type are added"() {
    given:
      def builder = TransactionalOutboxBuilder.make(clock)

    and:
      def handlerA = new DummyOutboxHandler(clock)
      def handlerB = new DummyOutboxHandler(clock)
      def handlers = Set.of(handlerA, handlerB)

    when:
      builder
        .withHandlers(handlers)
        .withMonitorLocksProvider(monitorLocksProvider)
        .withCleanupLocksProvider(cleanupLocksProvider)
        .withStore(store)
        .withInstantOutboxPublisher(instantOutboxPublisher)
        .build()

    then:
      thrown(IllegalArgumentException)
  }
}
