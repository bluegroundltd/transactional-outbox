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

  OutboxLocksProvider locksProvider = Mock()
  OutboxStore store = Mock()
  InstantOutboxPublisher instantOutboxPublisher = Mock()
  OutboxItemFactory outboxItemFactory = Mock()
  TransactionalOutbox transactionalOutbox

  def setup() {
    transactionalOutbox = new TransactionalOutboxImpl(
      clock,
      handlers,
      locksProvider,
      store,
      instantOutboxPublisher,
      outboxItemFactory,
      DURATION_ONE_HOUR,
      new FixedThreadPoolExecutorServiceFactory(1, "").make(),
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
      1 * locksProvider.acquire()
      1 * store.fetch(_) >> { OutboxFilter filter ->
        with(filter) {
          outboxPendingFilter.nextRunLessThan == now
          outboxRunningFilter.rerunAfterGreaterThan == now
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
      1 * locksProvider.release()
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
      1 * locksProvider.acquire()
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
      1 * locksProvider.release()
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
}
