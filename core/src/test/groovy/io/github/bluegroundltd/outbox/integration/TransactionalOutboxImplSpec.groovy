package io.github.bluegroundltd.outbox.integration

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.OutboxProcessingHostComposer
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.executor.FixedThreadPoolExecutorServiceFactory
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.utils.DummyOutboxHandler
import io.github.bluegroundltd.outbox.utils.DummyOutboxType
import io.github.bluegroundltd.outbox.utils.FailingOutboxHandler
import io.github.bluegroundltd.outbox.utils.InMemoryOutboxStore
import io.github.bluegroundltd.outbox.utils.MockOutboxHandler
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import io.github.bluegroundltd.outbox.utils.SimpleOutboxType
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class TransactionalOutboxImplSpec extends Specification {
  private final static Duration DURATION_ONE_HOUR = Duration.ofHours(1)
  private final static Duration DURATION_ONE_NANO = Duration.ofNanos(1)
  private final static Clock CLOCK = Clock.fixed(Instant.now(), ZoneId.systemDefault())

  private final static OutboxHandler DUMMY_HANDLER =
    new DummyOutboxHandler(CLOCK)
  private final static OutboxHandler SUCCEEDING_HANDLER =
    new MockOutboxHandler(new SimpleOutboxType("succeeding"), CLOCK)
  private final static OutboxHandler RETRYABLE_FAILURE_HANDLER =
    new FailingOutboxHandler(new SimpleOutboxType("retryableFailure"), CLOCK, false)
  private final static OutboxHandler TERMINAL_FAILURE_HANDLER =
    new FailingOutboxHandler(new SimpleOutboxType("terminalFailure"), CLOCK, true)
  private final static List<OutboxHandler> HANDLERS =
    [DUMMY_HANDLER, SUCCEEDING_HANDLER, RETRYABLE_FAILURE_HANDLER, TERMINAL_FAILURE_HANDLER]
  private final static Map<OutboxType, OutboxHandler> HANDLER_MAP =
    HANDLERS.collectEntries { [it.getSupportedType(), it] }

  private final OutboxLocksProvider monitorLocksProvider = Mock()
  private final OutboxLocksProvider cleanupLocksProvider = Mock()
  private final InMemoryOutboxStore store = new InMemoryOutboxStore()
  private final InstantOutboxPublisher instantOutboxPublisher = Mock()
  private final OutboxItemFactory outboxItemFactory = Mock()
  private final OutboxProcessingHostComposer processingHostComposer = new OutboxProcessingHostComposer()

  private final TransactionalOutbox transactionalOutbox = new TransactionalOutboxImpl(
    CLOCK,
    HANDLER_MAP,
    monitorLocksProvider,
    cleanupLocksProvider,
    store,
    instantOutboxPublisher,
    outboxItemFactory,
    DURATION_ONE_HOUR,
    new FixedThreadPoolExecutorServiceFactory(1, null, "").make(),
    [],
    DURATION_ONE_NANO,
    processingHostComposer
  )

  def "Should process all eligible items when [monitor] is invoked and set their statuses to 'COMPLETED'"() {
    given:
      def items = (1..5).collect { makePendingOutboxItem() }
      items.forEach { store.insert(it) }

    when:
      transactionalOutbox.monitor()

    then:
      1 * monitorLocksProvider.acquire()
      1 * monitorLocksProvider.release()
      0 * _

    and:
      new PollingConditions(timeout: 10, delay: 0.5).eventually {
        def updatedItems = store.get(items.collect { it.id })
        updatedItems.each {
          assert it.status == OutboxStatus.COMPLETED
        }
      }
  }

  def "Should appropriately set the status of all processed items when [monitor] is invoked"() {
    given: "Some items of various types"
      def itemsWithSucceedingType = (1..2).collect { makePendingOutboxItem(SUCCEEDING_HANDLER.getSupportedType()) }
      def itemsWithRetryableFailureType = (1..2).collect { makePendingOutboxItem(RETRYABLE_FAILURE_HANDLER.getSupportedType()) }
      def itemsWithTerminalFailureType = (1..2).collect { makePendingOutboxItem(TERMINAL_FAILURE_HANDLER.getSupportedType()) }
      def items = itemsWithSucceedingType + itemsWithRetryableFailureType + itemsWithTerminalFailureType
      items.forEach { store.insert(it) }

    when:
      transactionalOutbox.monitor()

    then:
      1 * monitorLocksProvider.acquire()
      1 * monitorLocksProvider.release()
      0 * _

    and:
      new PollingConditions(timeout: 10, delay: 0.5).eventually {
        def updatedItems = store.get(items.collect { it.id })
        updatedItems.each {
          assert it.status == expectedStatus(it)
        }
      }
  }

  def "Should early return from cleanup if in shutdown mode"() {
    when:
      transactionalOutbox.shutdown()
      transactionalOutbox.cleanup()

    then:
      0 * _
  }

  private final static OutboxItem makePendingOutboxItem(OutboxType type = null) {
    OutboxItemBuilder.make().withType(type ?: new DummyOutboxType()).withStatus(OutboxStatus.PENDING).build()
  }

  private final static OutboxStatus expectedStatus(OutboxItem item) {
    switch (item.type) {
      case SUCCEEDING_HANDLER.getSupportedType():
        return OutboxStatus.COMPLETED
      case RETRYABLE_FAILURE_HANDLER.getSupportedType():
        return OutboxStatus.PENDING
      case TERMINAL_FAILURE_HANDLER.getSupportedType():
        return OutboxStatus.FAILED
      default:
        return null
    }
  }
}
