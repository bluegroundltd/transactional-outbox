package io.github.bluegroundltd.outbox.integration

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.OutboxProcessingHostBuilder
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.executor.FixedThreadPoolExecutorServiceFactory
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.utils.DelayingOutboxHandler
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class TransactionalOutboxImplSpec extends Specification {
  private static final Duration DURATION_ONE_HOUR = Duration.ofHours(1)
  private static final Duration DURATION_ONE_NANO = Duration.ofNanos(1)
  private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneId.systemDefault())

  private OutboxHandler handler = new DelayingOutboxHandler(CLOCK)
  private OutboxType type = handler.getSupportedType()
  private Map<OutboxType, OutboxHandler> handlers = Map.of(type, handler)

  private OutboxLocksProvider monitorLocksProvider = Mock()
  private OutboxLocksProvider cleanupLocksProvider = Mock()
  private OutboxStore store = Mock()
  private InstantOutboxPublisher instantOutboxPublisher = Mock()
  private OutboxItemFactory outboxItemFactory = Mock()
  private OutboxProcessingHostBuilder processingHostBuilder = Mock()

  private TransactionalOutbox transactionalOutbox

  def setup() {
    transactionalOutbox = new TransactionalOutboxImpl(
      CLOCK,
      handlers,
      monitorLocksProvider,
      cleanupLocksProvider,
      store,
      instantOutboxPublisher,
      outboxItemFactory,
      DURATION_ONE_HOUR,
      new FixedThreadPoolExecutorServiceFactory(1, "").make(),
      [],
      DURATION_ONE_NANO,
      processingHostBuilder
    )
  }

  def "Should early return from cleanup if in shutdown mode"() {
    when:
      transactionalOutbox.shutdown()
      transactionalOutbox.cleanup()

    then:
      0 * _
  }
}
