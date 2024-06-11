package io.github.bluegroundltd.outbox.integration

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.executor.FixedThreadPoolExecutorServiceFactory
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.store.OutboxFilter
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.utils.DummyHandler
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class TransactionalOutboxImplSpec extends Specification {
  private static final Duration DURATION_ONE_HOUR = Duration.ofHours(1)
  private static final Duration DURATION_ONE_NANO = Duration.ofNanos(1)
  Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  OutboxHandler handler = new DummyHandler()
  OutboxType type = handler.getSupportedType()
  Map<OutboxType, OutboxHandler> handlers = Map.of(type, handler)

  OutboxLocksProvider monitorLocksProvider = Mock()
  OutboxLocksProvider cleanupLocksProvider = Mock()
  OutboxStore store = Mock()
  InstantOutboxPublisher instantOutboxPublisher = Mock()
  OutboxItemFactory outboxItemFactory = Mock()
  TransactionalOutbox transactionalOutbox

  def setup() {
    transactionalOutbox = new TransactionalOutboxImpl(
      clock,
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

  def "Should return when shutdown is called and the executor is empty"() {
    when:
      transactionalOutbox.shutdown()

    then:
      0 * _
  }

  def "Should force outbox shutdown when shutdown is called and the timeout elapsed before termination"() {
    given:
      def expectedOutboxItem =
        OutboxItemBuilder.make().withType(type).build()

    and:
      def now = Instant.now(clock)

    when:
      transactionalOutbox.monitor()

    then:
      1 * monitorLocksProvider.acquire()
      1 * store.fetch(_) >> { OutboxFilter filter ->
        with(filter) {
          outboxPendingFilter.nextRunLessThan == now
          outboxRunningFilter.rerunAfterLessThan == now
        }
        [expectedOutboxItem]
      }
      1 * store.update(_) >> { OutboxItem item ->
        with(item) {
          it.status == OutboxStatus.RUNNING
          it.lastExecution == now
          it.rerunAfter == item.lastExecution + DURATION_ONE_HOUR
        }
        return item
      }
      1 * monitorLocksProvider.release()
      0 * _

    when:
      transactionalOutbox.shutdown()

    then:
      0 * _
  }

  def "Should delegate to the outbox store when shutdown is called and there are tasks that didn't start execution"() {
    given:
      def pendingItem = OutboxItemBuilder.make().withType(type).build()
      def items = [pendingItem, pendingItem]
      def now = Instant.now(clock)

    when:
      transactionalOutbox.monitor()

    then:
      1 * monitorLocksProvider.acquire()
      1 * store.fetch(_) >> { OutboxFilter filter ->
        with(filter) {
          outboxPendingFilter.nextRunLessThan == now
        }
        items
      }
      items.size() * store.update(_) >> { OutboxItem item ->
        with(item) {
          it.status == OutboxStatus.RUNNING
          it.lastExecution == now
          it.rerunAfter == item.lastExecution + DURATION_ONE_HOUR
        }
        return item
      }
      1 * monitorLocksProvider.release()
      0 * _

    when:
      transactionalOutbox.shutdown()

    then:
      1 * store.update(_) >> { OutboxItem item ->
        with(item) {
          it.status == OutboxStatus.PENDING
          it.nextRun == now
          it.rerunAfter == null
        }
        return item
      }
      0 * _
  }

  def "Should delegate to outbox store when `cleanup` is invoked"() {
    given:
      def now = Instant.now(clock)

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
