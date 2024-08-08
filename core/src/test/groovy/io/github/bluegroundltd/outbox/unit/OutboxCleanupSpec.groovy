package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.processing.OutboxProcessingHostComposer
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.store.OutboxStore
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ExecutorService

class OutboxCleanupSpec extends Specification {
  private static final Duration DURATION_ONE_HOUR = Duration.ofHours(1)

  private final Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))
  private final Instant now = Instant.now(clock)

  private final OutboxLocksProvider monitorLocksProvider = Mock()
  private final OutboxLocksProvider cleanupLocksProvider = Mock()
  private final OutboxStore store = Mock()

  private final TransactionalOutbox transactionalOutbox = new TransactionalOutboxImpl(
    clock,
    [:],
    monitorLocksProvider,
    cleanupLocksProvider,
    store,
    Mock(InstantOutboxPublisher),
    Mock(OutboxItemFactory),
    DURATION_ONE_HOUR,
    Mock(ExecutorService),
    [],
    Duration.ofMillis(5000),
    Mock(OutboxProcessingHostComposer)
  )

  def "Should acquire the clean up lock, delete all completed items and release it"() {
    when:
      transactionalOutbox.cleanup()

    then:
      1 * cleanupLocksProvider.acquire()
      1 * store.deleteCompletedItems(now)
      1 * cleanupLocksProvider.release()
      0 * _
  }

  def "Should do nothing when it fails to acquire the cleanup lock"() {
    when:
      transactionalOutbox.cleanup()

    then:
      1 * cleanupLocksProvider.acquire() >> {
        throw new InterruptedException()
      }
      0 * _
      noExceptionThrown()
  }

  def "Should handle an exception thrown when deleting the completed items"() {
    when:
      transactionalOutbox.cleanup()

    then:
      1 * cleanupLocksProvider.acquire()
      1 * store.deleteCompletedItems(now) >> {
        throw new InterruptedException()
      }
      1 * cleanupLocksProvider.release()
      0 * _

    and:
      noExceptionThrown()
  }

  def "Should handle an exception thrown when releasing the cleanup lock"() {
    when:
      transactionalOutbox.cleanup()

    then:
      1 * cleanupLocksProvider.acquire()
      1 * store.deleteCompletedItems(now)
      1 * cleanupLocksProvider.release() >> {
        throw new InterruptedException()
      }
      0 * _

    and:
      noExceptionThrown()
  }
}
