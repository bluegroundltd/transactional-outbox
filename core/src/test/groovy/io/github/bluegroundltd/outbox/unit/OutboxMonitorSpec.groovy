package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.event.OnDemandOutboxPublisher
import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.store.OutboxFilter
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class OutboxMonitorSpec extends Specification {
  private static final Duration DURATION_ONE_HOUR = Duration.ofHours(1)
  Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  Map<OutboxType, OutboxHandler> handlers = Mock()
  OutboxLocksProvider locksProvider = Mock()
  OutboxStore store = Mock()
  OnDemandOutboxPublisher onDemandOutboxPublisher = Mock()
  OutboxItemFactory outboxItemFactory = Mock()
  ExecutorService executor = Mock()
  Duration threadPoolTimeOut = Duration.ofMillis(5000)
  TransactionalOutbox transactionalOutbox

  def setup() {
    transactionalOutbox = new TransactionalOutboxImpl(
      clock,
      handlers,
      locksProvider,
      store,
      onDemandOutboxPublisher,
      outboxItemFactory,
      DURATION_ONE_HOUR,
      executor,
      threadPoolTimeOut
    )
  }

  def "Should delegate to outbox store when add is called"() {
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)

    and:
      def outboxItem = OutboxItemBuilder.make().build()

    when:
      transactionalOutbox.add(type, payload)

    then:
      1 * type.getType() >> "type"
      1 * outboxItemFactory.makeScheduledOutboxItem(type, payload) >> outboxItem
      1 * store.insert(outboxItem)
      0 * _
  }

  def "Should delegate to outbox store and publisher when addOnDemand is called"() {
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)

    and:
      def outboxItem = OutboxItemBuilder.make().build()
      def savedOutbox = OutboxItemBuilder.make().build()

    when:
      transactionalOutbox.addOnDemandOutbox(type, payload)

    then:
      1 * type.getType() >> "type"
      1 * outboxItemFactory.makeOnDemandOutboxItem(type, payload) >> outboxItem
      1 * store.insert(outboxItem) >> savedOutbox
      1 * onDemandOutboxPublisher.publish({
        assert it.outbox == savedOutbox
      })
      0 * _
  }

  def "Should return when monitor is called after a shutdown request"() {
    when:
      transactionalOutbox.shutdown()

    then:
      1 * executor.shutdown()
      1 * executor.awaitTermination(threadPoolTimeOut.toSeconds(), TimeUnit.SECONDS) >> true

    when:
      transactionalOutbox.monitor()

    then:
      0 * _
  }

  def "Should handle a failure while an on-demand outbox is being processed"() {
    given:
      def onDemandOutbox = OutboxItemBuilder.make().build()

    and:
      def expectedHandler = GroovyMock(OutboxHandler)

    when:
      transactionalOutbox.handleOnDemandOutbox(onDemandOutbox)

    then:
      1 * handlers.get(_) >> expectedHandler
      1 * executor.execute(_) >> { throw new RuntimeException() }
      0 * _

    and:
      noExceptionThrown()
  }

  def "Should delegate to the executor thread pool when monitor is called"() {
    given:
      def pendingItem = OutboxItemBuilder.makePending()
      def runningItem = OutboxItemBuilder.make().withStatus(OutboxStatus.RUNNING).build()
      def items = [pendingItem, runningItem]

    and:
      def expectedHandler = GroovyMock(OutboxHandler)
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
      items.size() * handlers.get(_) >> expectedHandler
      items.size() * executor.execute(_)
      0 * _
  }

  def "Should delegate to the outbox store when monitor is called and the executor rejects the tasks"() {
    given:
      def pendingItem = OutboxItemBuilder.makePending()

    and:
      def expectedHandler = GroovyMock(OutboxHandler)
      def now = Instant.now(clock)

    when:
      transactionalOutbox.monitor()

    then:
      1 * locksProvider.acquire()
      1 * store.fetch(_) >> { OutboxFilter filter ->
        with(filter) {
          outboxPendingFilter.nextRunLessThan == now
        }
        [pendingItem]
      }
      1 * store.update(_) >> { OutboxItem item ->
        with(item) {
          item.status == OutboxStatus.RUNNING
          item.lastExecution == now
          item.rerunAfter == item.lastExecution + DURATION_ONE_HOUR
        }
      }
      1 * locksProvider.release()
      1 * handlers.get(_) >> expectedHandler
      1 * executor.execute(_) >> { throw new RejectedExecutionException() }
      1 * store.update(_) >> { OutboxItem item ->
        with(item) {
          item.status == OutboxStatus.PENDING
          item.nextRun == now
          item.rerunAfter == null
        }
      }
      0 * _
  }

  def "Should do nothing with an item that is erroneously fetched"() {
    given:
      def completedItem = OutboxItemBuilder.make().withStatus(OutboxStatus.COMPLETED).build()
      def items = [completedItem]

    when:
      transactionalOutbox.monitor()

    then:
      1 * locksProvider.acquire()
      1 * store.fetch(_) >> items
      1 * locksProvider.release()
      0 * _
  }

  def "Should call release when an exception occurs"() {
    when:
      transactionalOutbox.monitor()

    then:
      1 * locksProvider.acquire()
      1 * store.fetch(_) >> { throw new RuntimeException() }
      1 * locksProvider.release()
      0 * _
  }

  def "Should handle an exception when it occurs upon release"() {
    when:
      transactionalOutbox.monitor()

    then:
      1 * locksProvider.acquire()
      1 * store.fetch(_) >> []
      1 * locksProvider.release() >> { throw new RuntimeException() }
      0 * _
      noExceptionThrown()
  }
}
