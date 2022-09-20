package com.blueground.outbox

import com.blueground.outbox.item.OutboxType
import com.blueground.outbox.store.OutboxStore
import com.blueground.outbox.utils.DummyOutboxHandler
import com.blueground.outbox.utils.UnitTestSpecification
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TransactionalOutboxBuilderSpec extends UnitTestSpecification {
  def clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  def lockIdentifier = 1L
  def locksProvider = GroovyMock(OutboxLocksProvider)
  def store = GroovyMock(OutboxStore)

  @Unroll
  def "Should build a transactional outbox instance #testCase"() {
    given:
      def builder = TransactionalOutboxBuilder.make(clock)

    and:
      def handlerA = new DummyOutboxHandler()
      def handlerB = GroovyMock(OutboxHandler)
      def mockedBType = GroovyMock(OutboxType)
      def handlers = Set.of(handlerA, handlerB)
      def expectedOutboxTypes = Set.of(handlerA.getSupportedType(), mockedBType)


    when:
      def transactionalOutboxBuilder = builder
        .withHandlers(handlers)
        .withLocksProvider(lockIdentifier, locksProvider)
        .withStore(store)

      and:
        TransactionalOutbox transactionalOutbox
        if (withCustomThreadPoolSize) {
          transactionalOutbox = transactionalOutboxBuilder.withThreadPoolSize(5).build()
        } else {
          transactionalOutbox = transactionalOutboxBuilder.build()
        }

    and:
      def mappedHandlers = builder.getHandlers() as Map<OutboxType, OutboxHandler>

    then:
      transactionalOutbox instanceof TransactionalOutboxImpl
      2 * handlerB.getSupportedType() >> mockedBType
      mappedHandlers.size() == 2
      mappedHandlers.keySet() == expectedOutboxTypes

    where:
      testCase                        | withCustomThreadPoolSize
      "with default thread pool size" | false
      "with custom thread pool size"  | true
  }

  def "Should throw if handlers with same supporting type are added"() {
    given:
      def builder = TransactionalOutboxBuilder.make(clock)

    and:
      def handlerA = new DummyOutboxHandler()
      def handlerB = new DummyOutboxHandler()
      def handlers = Set.of(handlerA, handlerB)

    when:
      builder
        .withHandlers(handlers)
        .withLocksProvider(lockIdentifier, locksProvider)
        .withStore(store)
        .build()

    then:
      thrown(IllegalArgumentException)
  }
}
