package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.grouping.OutboxGroupingProvider
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxItemGroup
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.processing.OutboxGroupProcessor
import io.github.bluegroundltd.outbox.processing.OutboxItemProcessorDecorator
import io.github.bluegroundltd.outbox.processing.OutboxProcessingAction
import io.github.bluegroundltd.outbox.processing.OutboxProcessingHost
import io.github.bluegroundltd.outbox.processing.OutboxProcessingHostComposer
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

  private Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  private OutboxLocksProvider monitorLocksProvider = Mock()
  private OutboxStore store = Mock()
  private ExecutorService executor = Mock()
  private List<OutboxItemProcessorDecorator> decorators = (1..5).collect { Mock(OutboxItemProcessorDecorator) }
  private Duration threadPoolTimeOut = Duration.ofMillis(5000)
  private OutboxProcessingHostComposer processingHostComposer = Mock()
  private final OutboxGroupingProvider groupingProvider = Mock()

  private TransactionalOutbox transactionalOutbox

  def setup() {
    transactionalOutbox = makeTransactionalOutbox(false)
  }

  def "Should delegate to the executor thread pool when an instant outbox is processed and `instantProcessingEnabled` is false"() {
    given:
      def instantOutbox = OutboxItemBuilder.makePending().build().with {
        it.prepareForProcessing(Instant.now(clock), Instant.now(clock) + DURATION_ONE_HOUR)
        it
      }
      def processingHost = Mock(OutboxProcessingHost)

    when:
      transactionalOutbox.processInstantOutbox(instantOutbox)

    then:
      1 * processingHostComposer.compose(_, _) >> {
        OutboxProcessingAction action, List<OutboxItemProcessorDecorator> decorators ->
          assert action instanceof OutboxGroupProcessor
          assert decorators == this.decorators
          return processingHost
      }
      1 * executor.execute(processingHost)
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
      def instantOutbox = OutboxItemBuilder.makePending().build()
      def processingHost = Mock(OutboxProcessingHost)

    when:
      transactionalOutbox.processInstantOutbox(instantOutbox)

    then:
      1 * processingHostComposer.compose(_, _) >> {
        OutboxProcessingAction action, List<OutboxItemProcessorDecorator> decorators ->
          assert action instanceof OutboxGroupProcessor
          assert decorators == this.decorators
          return processingHost
      }
      1 * executor.execute(processingHost) >> { throw new RuntimeException() }
      0 * _

    and:
      noExceptionThrown()
  }

  def "Should group items and delegate to the executor thread pool when monitor is called"() {
    given:
      def now = Instant.now(clock)
      def pendingItem = OutboxItemBuilder.makePending(now).build()
      def runningItem = OutboxItemBuilder.makeRunning(now).build()
      def failedItem = OutboxItemBuilder.makeFailed().build()
      def completedItem = OutboxItemBuilder.makeCompleted().build()

    and:
      def fetchedItems = [pendingItem, runningItem, failedItem, completedItem]
      def processableItems = [pendingItem, runningItem, failedItem]
      def itemGroups = [
        [pendingItem],
        [runningItem, failedItem]
      ]
      def eligibleItems = [pendingItem, runningItem]

    and:
      def processingHosts = itemGroups.collect { Mock(OutboxProcessingHost) }

    when:
      transactionalOutbox.monitor()

    then:
      1 * monitorLocksProvider.acquire()
      1 * store.fetch(_) >> { OutboxFilter filter ->
        with(filter) {
          outboxPendingFilter.nextRunLessThan == now
          outboxRunningFilter.rerunAfterLessThan == now
        }
        fetchedItems
      }

    and: "The items marked for processing are grouped"
      1 * groupingProvider.execute(_) >> {
        // Spock gets confused by list parameters and even if we explicitly specify them (i.e. use named parameters)
        // will still wrap it in an array.
        // To avoid confusion we skip naming the parameters and instead access them by index.
        def items = (it as List)[0] as List<OutboxItem>
        assert items == processableItems
        return itemGroups.collect {
          new OutboxItemGroup(findMatchingItems(items, it))
        }
      }

    and: "The pending and running items are marked for processing while the failed item is stored as-is"
      eligibleItems.size() * store.update(_) >> { OutboxItem item ->
        with(item) {
          it.status == OutboxStatus.RUNNING
          it.lastExecution == now
          it.rerunAfter == item.lastExecution + DURATION_ONE_HOUR
        }
        return item
      }
      1 * store.update(_) >> { OutboxItem item ->
        with(item) {
          it.id == failedItem.id
          it.status == OutboxStatus.FAILED
          it.lastExecution == failedItem.lastExecution
          it.rerunAfter == failedItem.rerunAfter
        }
        return item
      }

    and: "A processor is created for each group and submitted for execution"
      itemGroups.eachWithIndex { group, index ->
        1 * processingHostComposer.compose(_, _) >> { OutboxProcessingAction action, List<OutboxItemProcessorDecorator> decorators ->
          assert action instanceof OutboxGroupProcessor
          assert decorators == this.decorators
          return processingHosts[index]
        }
        1 * executor.execute(processingHosts[index])
      }

    and:
      1 * monitorLocksProvider.release()
      0 * _
  }

  def "Should reset the outbox item when monitor is called and the executor rejects the tasks"() {
    given:
      def now = Instant.now(clock)

    and:
      def itemBuilder = OutboxItemBuilder.makePending(now)
      def pendingItem = itemBuilder.build()
      def originalItem = itemBuilder.build() // Create a clone to use it for comparisons.

    and:
      def processingHost = Mock(OutboxProcessingHost)

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
      1 * groupingProvider.execute(_) >> {
        // Spock gets confused by list parameters and even if we explicitly specify them (i.e. use named parameters)
        // will still wrap it in an array.
        // To avoid confusion we skip naming the parameters and instead access them by index.
        def items = (it as List)[0] as List<OutboxItem>
        assert items == [pendingItem]
        return [new OutboxItemGroup(items)]
      }
      1 * store.update(_) >> { OutboxItem item ->
        assert item == originalItem.with {
          status = OutboxStatus.RUNNING
          lastExecution = now
          rerunAfter = now + DURATION_ONE_HOUR
          it
        }
        return item
      }
      1 * processingHostComposer.compose(_, _) >> { OutboxProcessingAction action, List<OutboxItemProcessorDecorator> decorators ->
        assert action instanceof OutboxGroupProcessor
        assert decorators == this.decorators
        return processingHost
      }
      1 * executor.execute(processingHost) >> { throw new RejectedExecutionException() }
      1 * processingHost.reset()
      1 * monitorLocksProvider.release()
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
      1 * groupingProvider.execute([]) >> []
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
      1 * groupingProvider.execute([]) >> []
      1 * monitorLocksProvider.release() >> { throw new RuntimeException() }
      0 * _
      noExceptionThrown()
  }

  def "Should process instant outbox items using monitor but filter fetched items by instant outbox id (hint) and `instantProcessingEnabled` is true"() {
    given:
      transactionalOutbox = makeTransactionalOutbox(true)

    and:
      def instantOutbox = OutboxItemBuilder.makePending().build()
      def irrelevantOutbox = OutboxItemBuilder.makePending().build()
      def fetchedItems = [irrelevantOutbox, instantOutbox]
      def processingHost = Mock(OutboxProcessingHost)
      def now = Instant.now(clock)

    when:
      transactionalOutbox.processInstantOutbox(instantOutbox)

    then:
      1 * monitorLocksProvider.acquire()
      1 * store.fetch(_) >> { OutboxFilter filter ->
        with(filter) {
          outboxPendingFilter.nextRunLessThan == now
          outboxRunningFilter.rerunAfterLessThan == now
          id == instantOutbox.id
        }
        fetchedItems
      }
      1 * groupingProvider.execute(_) >> {
        // Spock gets confused by list parameters and even if we explicitly specify them (i.e. use named parameters)
        // will still wrap it in an array.
        // To avoid confusion we skip naming the parameters and instead access them by index.
        def items = (it as List)[0] as List<OutboxItem>
        assert items == fetchedItems
        return items.collect { OutboxItemGroup.of(it) }
      }
      1 * store.update(_) >> { OutboxItem updatedItem ->
        with(updatedItem) {
          it.id == instantOutbox.id
          it.status == OutboxStatus.RUNNING
          it.lastExecution == now
          it.rerunAfter == updatedItem.lastExecution + DURATION_ONE_HOUR
        }
        updatedItem
      }
      1 * processingHostComposer.compose(_, _) >> {
        OutboxProcessingAction action, List<OutboxItemProcessorDecorator> decorators ->
          assert action instanceof OutboxGroupProcessor
          assert decorators == this.decorators
          processingHost
      }
      1 * executor.execute(processingHost)
      1 * monitorLocksProvider.release()
      0 * _
  }

  private TransactionalOutboxImpl makeTransactionalOutbox(Boolean instantProcessingEnabled) {
    new TransactionalOutboxImpl(
      clock,
      [:],
      monitorLocksProvider,
      Mock(OutboxLocksProvider),
      store,
      Mock(InstantOutboxPublisher),
      Mock(OutboxItemFactory),
      DURATION_ONE_HOUR,
      executor,
      decorators,
      threadPoolTimeOut,
      processingHostComposer,
      instantProcessingEnabled,
      groupingProvider
    )
  }

  private static List<OutboxItem> findMatchingItems(List<OutboxItem> updatedItems, List<OutboxItem> originalItems) {
    return updatedItems.findAll { item -> originalItems.contains(item) }
  }
}
