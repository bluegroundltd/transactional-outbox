package io.github.bluegroundltd.outbox.integration

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.executor.FixedThreadPoolExecutorServiceFactory
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.utils.DummyHandler
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class TransactionalOutboxImplSpec extends Specification {
  private static final Duration DURATION_ONE_HOUR = Duration.ofHours(1)
  private static final Duration DURATION_ONE_NANO = Duration.ofNanos(1)
  private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneId.systemDefault())

  private OutboxHandler handler = new DummyHandler()
  private OutboxType type = handler.getSupportedType()
  private Map<OutboxType, OutboxHandler> handlers = Map.of(type, handler)

  private OutboxLocksProvider monitorLocksProvider = Mock()
  private OutboxLocksProvider cleanupLocksProvider = Mock()
  private OutboxStore store = Mock()
  private InstantOutboxPublisher instantOutboxPublisher = Mock()
  private OutboxItemFactory outboxItemFactory = Mock()

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
      DURATION_ONE_NANO
    )
  }

  def "Should delegate to outbox store when `cleanup` is invoked"() {
    given:
      def now = Instant.now(CLOCK)

    when:
      transactionalOutbox.cleanup()

    then:
      1 * cleanupLocksProvider.acquire()
      1 * store.deleteCompletedItems(now)
      1 * cleanupLocksProvider.release()
      0 * _
  }

  def "Should early return from cleanup if in shutdown mode"() {
    when:
      transactionalOutbox.shutdown()
      transactionalOutbox.cleanup()

    then:
      0 * _
  }

  def "Should handle an exception thrown from the cleanup store method"() {
    when:
      transactionalOutbox.cleanup()

    then:
      1 * cleanupLocksProvider.acquire()
      1 * store.deleteCompletedItems(_) >> {
        throw new InterruptedException()
      }
      1 * cleanupLocksProvider.release()
      0 * _
      noExceptionThrown()
  }

  def "Should handle an exception thrown during the cleanup release locks"() {
    when:
      transactionalOutbox.cleanup()

    then:
      1 * cleanupLocksProvider.acquire()
      1 * store.deleteCompletedItems(_)
      1 * cleanupLocksProvider.release() >> {
        throw new InterruptedException()
      }
      0 * _
      noExceptionThrown()
  }

  def "Should not release the lock in cleanup, after a failure in acquire"() {
    when:
      transactionalOutbox.cleanup()

    then:
      1 * cleanupLocksProvider.acquire() >> {
        throw new InterruptedException()
      }
      0 * _
      noExceptionThrown()
  }
}
