package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxBuilder
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.grouping.DefaultGroupingConfiguration
import io.github.bluegroundltd.outbox.grouping.GroupIdGroupingProvider
import io.github.bluegroundltd.outbox.grouping.OutboxGroupIdProvider
import io.github.bluegroundltd.outbox.grouping.OutboxGroupingConfiguration
import io.github.bluegroundltd.outbox.grouping.OutboxGroupingProvider
import io.github.bluegroundltd.outbox.grouping.NullGroupIdProvider
import io.github.bluegroundltd.outbox.grouping.SingleItemGroupingConfiguration
import io.github.bluegroundltd.outbox.grouping.SingleItemGroupingProvider
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
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

    and:
      def itemFactory = transactionalOutbox.outboxItemFactory
      itemFactory != null
      itemFactory instanceof OutboxItemFactory

    and:
      def groupIdProvider = itemFactory.groupIdProvider
      groupIdProvider == builder.groupIdProvider
      groupIdProvider instanceof NullGroupIdProvider

    and:
      def groupingProvider = transactionalOutbox.groupingProvider
      groupingProvider == builder.groupingConfiguration.groupingProvider
      groupingProvider == DefaultGroupingConfiguration.INSTANCE.groupingProvider
      groupingProvider instanceof GroupIdGroupingProvider

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

  def "Should set the group id provider of the item factory to the one specified"() {
    given:
      def builder = TransactionalOutboxBuilder.make(clock)
      def groupIdProvider = Mock(OutboxGroupIdProvider)

    when:
      def transactionalOutbox = builder
        .withHandlers(Set.of(new DummyOutboxHandler()))
        .withMonitorLocksProvider(monitorLocksProvider)
        .withCleanupLocksProvider(cleanupLocksProvider)
        .withStore(store)
        .withInstantOutboxPublisher(instantOutboxPublisher)
        .withGroupIdProvider(groupIdProvider)
        .build()

    then:
      0 * _

    and:
      def itemFactory = transactionalOutbox.outboxItemFactory
      itemFactory != null
      itemFactory instanceof OutboxItemFactory
      itemFactory.groupIdProvider == groupIdProvider
  }

  def "Should set the grouping provider to the one specified in the supplied grouping configuration"() {
    given:
      def builder = TransactionalOutboxBuilder.make(clock)
      def groupingProvider = Mock(OutboxGroupingProvider)
      def groupingConfiguration = Mock(OutboxGroupingConfiguration)

    when:
      def transactionalOutbox = builder
        .withHandlers(Set.of(new DummyOutboxHandler()))
        .withMonitorLocksProvider(monitorLocksProvider)
        .withCleanupLocksProvider(cleanupLocksProvider)
        .withStore(store)
        .withInstantOutboxPublisher(instantOutboxPublisher)
        .withGroupingConfiguration(groupingConfiguration)
        .build()

    then:
      1 * groupingConfiguration.groupingProvider >> groupingProvider
      0 * _

    and:
      def outboxGroupingProvider = transactionalOutbox.groupingProvider
      outboxGroupingProvider == groupingProvider
  }

  def "Should use the default grouping provider when 'withGrouping' in applied"() {
    given:
      def builder = TransactionalOutboxBuilder.make(clock)

    when:
      def transactionalOutbox = builder
        .withHandlers(Set.of(new DummyOutboxHandler()))
        .withMonitorLocksProvider(monitorLocksProvider)
        .withCleanupLocksProvider(cleanupLocksProvider)
        .withStore(store)
        .withInstantOutboxPublisher(instantOutboxPublisher)
        .withGrouping()
        .build()

    then:
      0 * _

    and:
      def outboxGroupingProvider = transactionalOutbox.groupingProvider
      outboxGroupingProvider == DefaultGroupingConfiguration.INSTANCE.groupingProvider
      outboxGroupingProvider instanceof GroupIdGroupingProvider
  }

  def "Should use single item groping provider when 'withoutGrouping' is applied"() {
    given:
      def builder = TransactionalOutboxBuilder.make(clock)

    when:
      def transactionalOutbox = builder
        .withHandlers(Set.of(new DummyOutboxHandler()))
        .withMonitorLocksProvider(monitorLocksProvider)
        .withCleanupLocksProvider(cleanupLocksProvider)
        .withStore(store)
        .withInstantOutboxPublisher(instantOutboxPublisher)
        .withoutGrouping()
        .build()

    then:
      0 * _

    and:
      def outboxGroupingProvider = transactionalOutbox.groupingProvider
      outboxGroupingProvider == SingleItemGroupingConfiguration.INSTANCE.groupingProvider
      outboxGroupingProvider instanceof SingleItemGroupingProvider
  }
}
