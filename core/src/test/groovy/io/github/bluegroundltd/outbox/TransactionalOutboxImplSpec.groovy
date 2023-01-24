package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
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

class TransactionalOutboxImplSpec extends Specification {
  private static final Duration DURATION_ONE_HOUR = Duration.ofHours(1)
  Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  Map<OutboxType, OutboxHandler> handlers = Mock()
  OutboxLocksProvider locksProvider = Mock()
  OutboxStore store = Mock()
  ExecutorService executor = Mock()
  Duration threadPoolTimeOut = Duration.ofMillis(5000)
  TransactionalOutbox transactionalOutbox

  def setup() {
    transactionalOutbox = new TransactionalOutboxImpl(
      clock,
      handlers,
      locksProvider,
      store,
      DURATION_ONE_HOUR,
      executor,
      threadPoolTimeOut
    )
  }

  def "Should throw UnsupportedOperationException when item isn't supported"() {
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)

    when:
      transactionalOutbox.add(type, payload)

    then:
      1 * handlers.get(type) >> null
      2 * type.getType() >> "type"
      0 * _

    and:
      thrown(UnsupportedOperationException)
  }

  def "Should delegate to outbox store when add is called"() {
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)
      def handler = GroovyMock(OutboxHandler)

    and:
      def serializedPayload = "serializedPayload"
      def nextRun = GroovyMock(Instant)

    and:
      def expectedOutboxItem =
        OutboxItemBuilder.make().withType(type).withPayload(serializedPayload).withNextRun(nextRun).build()

    when:
      transactionalOutbox.add(type, payload)

    then:
      1 * type.getType() >> "type"
      1 * handlers.get(type) >> handler
      1 * handler.serialize(payload) >> serializedPayload
      1 * handler.getNextExecutionTime(0) >> nextRun
      1 * store.insert(_) >> { OutboxItem item ->
        with(item) {
          id == null
          type == expectedOutboxItem.type
          status == expectedOutboxItem.status
          item.payload == expectedOutboxItem.payload
          retries == expectedOutboxItem.retries
          nextRun == expectedOutboxItem.nextRun
          lastExecution == expectedOutboxItem.lastExecution
          rerunAfter == expectedOutboxItem.rerunAfter
        }
      }
      0 * _
  }

  def "Should return when monitor is called after a shutdown request"() {
    when:
      transactionalOutbox.shutdown()
      transactionalOutbox.monitor()

    then:
      1 * executor.shutdown()
      1 * executor.awaitTermination(threadPoolTimeOut.toSeconds(), TimeUnit.SECONDS) >> true
      0 * _
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
          item.status == OutboxStatus.RUNNING
          item.lastExecution == now
          item.rerunAfter == item.lastExecution + DURATION_ONE_HOUR
        }
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

  def "Should delegate to the outbox store when shutdown is called and the timeout elapsed before termination"() {
    given:
      def runningItem = OutboxItemBuilder.makeRunning()
      def handler = GroovyMock(OutboxHandler)
      def processor = new OutboxItemProcessor(runningItem, handler, store)
      def expected = [processor]
      def now = Instant.now(clock)

    when:
      transactionalOutbox.shutdown()

    then:
      1 * executor.shutdown()
      1 * executor.awaitTermination(threadPoolTimeOut.toSeconds(), TimeUnit.SECONDS) >> false
      1 * executor.shutdownNow() >> expected
      1 * store.update(_) >> { OutboxItem item ->
        with(item) {
          item.status == OutboxStatus.PENDING
          item.nextRun == now
          item.rerunAfter == null
        }
      }
      0 * _
      noExceptionThrown()
  }

  def "Should return when shutdown is called while already in shutdown mode"() {
    when:
      transactionalOutbox.shutdown()
      transactionalOutbox.shutdown()


    then:
      1 * executor.shutdown()
      1 * executor.awaitTermination(threadPoolTimeOut.toSeconds(), TimeUnit.SECONDS) >> true
      0 * _
  }

  def "Should do nothing when shutdown is called and all tasks have completed execution"() {
    when:
      transactionalOutbox.shutdown()

    then:
      1 * executor.shutdown()
      1 * executor.awaitTermination(threadPoolTimeOut.toSeconds(), TimeUnit.SECONDS) >> true
      0 * _
      noExceptionThrown()
  }

  def "Should throw an exception when it occurs while awaiting tasks to be executed or while shutting down"() {
    when:
      transactionalOutbox.shutdown()

    then:
      1 * executor.shutdown()
      1 * executor.awaitTermination(threadPoolTimeOut.toSeconds(), TimeUnit.SECONDS) >> { throw new InterruptedException() }
      thrown(InterruptedException)
      0 * _
  }
}
