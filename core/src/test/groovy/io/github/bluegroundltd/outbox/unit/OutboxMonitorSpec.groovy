package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxItemProcessorDecorator
import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.store.OutboxFilter
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification
import spock.lang.Unroll

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
  OutboxLocksProvider monitorLocksProvider = Mock()
  OutboxLocksProvider cleanupLocksProvider = Mock()
  OutboxStore store = Mock()
  InstantOutboxPublisher instantOutboxPublisher = Mock()
  OutboxItemFactory outboxItemFactory = Mock()
  ExecutorService executor = Mock()
  Duration threadPoolTimeOut = Duration.ofMillis(5000)
  TransactionalOutbox transactionalOutbox

  def setup() {
    transactionalOutbox = makeTransactionalOutbox()
  }

  private TransactionalOutboxImpl makeTransactionalOutbox(List<OutboxItemProcessorDecorator> decorators = []) {
    new TransactionalOutboxImpl(
      clock,
      handlers,
      monitorLocksProvider,
      cleanupLocksProvider,
      store,
      instantOutboxPublisher,
      outboxItemFactory,
      DURATION_ONE_HOUR,
      executor,
      decorators,
      threadPoolTimeOut
    )
  }

  def "Should delegate to the executor thread pool when an instant outbox is processed"(){
    given:
      def instantOutbox = OutboxItemBuilder.make().build()

    and:
      def expectedHandler = GroovyMock(OutboxHandler)

    when:
      transactionalOutbox.processInstantOutbox(instantOutbox)

    then:
      1 * handlers.get(_) >> expectedHandler
      1 * executor.execute(_)
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

  def "Should handle a failure while an instant outbox is being processed"() {
    given:
      def instantOutbox = OutboxItemBuilder.make().build()

    and:
      def expectedHandler = GroovyMock(OutboxHandler)

    when:
      transactionalOutbox.processInstantOutbox(instantOutbox)

    then:
      1 * handlers.get(_) >> expectedHandler
      1 * executor.execute(_) >> { throw new RuntimeException() }
      0 * _

    and:
      noExceptionThrown()
  }

  @Unroll
  def "Should delegate to the executor thread pool when monitor is called"() {
    given:
      def firstDecorator = null
      def secondDecorator = null
      def processorDecoratedByFirstDecorator = Mock(Runnable)
      List<OutboxItemProcessorDecorator> decorators = []
      if (hasDecorators) {
        firstDecorator = Mock(OutboxItemProcessorDecorator)
        secondDecorator = Mock(OutboxItemProcessorDecorator)
        decorators = [firstDecorator, secondDecorator]
      }
      transactionalOutbox = makeTransactionalOutbox(decorators)

    and:
      def pendingItem = OutboxItemBuilder.makePending()
      def runningItem = OutboxItemBuilder.make().withStatus(OutboxStatus.RUNNING).build()
      def items = [pendingItem, runningItem]

    and:
      def decoratorCalls = hasDecorators ? items.size() : 0

    and:
      def expectedHandler = GroovyMock(OutboxHandler)
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
      items.size() * handlers.get(_) >> expectedHandler
      decoratorCalls * firstDecorator.decorate(_) >> processorDecoratedByFirstDecorator
      decoratorCalls * secondDecorator.decorate(processorDecoratedByFirstDecorator) >> Mock(Runnable)
      items.size() * executor.execute(_)
      0 * _

    where:
      hasDecorators << [false, true]
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
      1 * monitorLocksProvider.acquire()
      1 * store.fetch(_) >> { OutboxFilter filter ->
        with(filter) {
          outboxPendingFilter.nextRunLessThan == now
        }
        [pendingItem]
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
      1 * handlers.get(_) >> expectedHandler
      1 * executor.execute(_) >> { throw new RejectedExecutionException() }
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

  def "Should do nothing with an item that is erroneously fetched"() {
    given:
      def completedItem = OutboxItemBuilder.make().withStatus(OutboxStatus.COMPLETED).build()
      def items = [completedItem]

    when:
      transactionalOutbox.monitor()

    then:
      1 * monitorLocksProvider.acquire()
      1 * store.fetch(_) >> items
      1 * monitorLocksProvider.release()
      0 * _
  }

  def "Should call release when an exception occurs"() {
    when:
      transactionalOutbox.monitor()

    then:
      1 * monitorLocksProvider.acquire()
      1 * store.fetch(_) >> { throw new RuntimeException() }
      1 * monitorLocksProvider.release()
      0 * _
  }

  def "Should handle an exception when it occurs upon release"() {
    when:
      transactionalOutbox.monitor()

    then:
      1 * monitorLocksProvider.acquire()
      1 * store.fetch(_) >> []
      1 * monitorLocksProvider.release() >> { throw new RuntimeException() }
      0 * _
      noExceptionThrown()
  }
}
