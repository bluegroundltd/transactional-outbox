package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxItemProcessorDecorator
import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.OutboxProcessingHost
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.store.OutboxFilter
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.utils.DummyOutboxHandler
import io.github.bluegroundltd.outbox.utils.DummyOutboxType
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

// The suite and the corresponding class are in heavy need of refactoring to allow for better testability.
// Some of the tests (e.g. "Should reset the outbox item when monitor is called and the executor rejects the tasks")
// do not work properly (i.e. they pass even when they should not).
// This seems to be related to not being able to mock the objects that do some of the processing (e.g.
// [OutboxItemProcessor], [OutboxProcessingHost]).
class OutboxMonitorSpec extends Specification {
  private static final Duration DURATION_ONE_HOUR = Duration.ofHours(1)
  Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  Map<OutboxType, OutboxHandler> handlers = [(new DummyOutboxType()): new DummyOutboxHandler()]
  OutboxLocksProvider monitorLocksProvider = Mock()
  OutboxLocksProvider cleanupLocksProvider = Mock()
  OutboxStore store = Mock()
  InstantOutboxPublisher instantOutboxPublisher = Mock()
  OutboxItemFactory outboxItemFactory = Mock()
  ExecutorService executor = Mock()
  Duration threadPoolTimeOut = Duration.ofMillis(5000)
  TransactionalOutbox transactionalOutbox

  def setup() {
    // For simplicity, we use no decorators since the decoration logic has been shifted to [OutboxProcessingHost].
    // Check [OutboxProcessingHostSpec] for the decoration logic tests.
    transactionalOutbox = new TransactionalOutboxImpl(
      clock,
      handlers,
      monitorLocksProvider,
      cleanupLocksProvider,
      store,
      instantOutboxPublisher,
      outboxItemFactory,
      DURATION_ONE_HOUR,
      executor,
      [],
      threadPoolTimeOut
    )
  }

  def "Should delegate to the executor thread pool when an instant outbox is processed"() {
    given:
      def instantOutbox = OutboxItemBuilder.make().build()

    when:
      transactionalOutbox.processInstantOutbox(instantOutbox)

    then:
      1 * executor.execute(_) >> { Runnable runnable ->
        assert runnable instanceof OutboxProcessingHost
      }
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

    when:
      transactionalOutbox.processInstantOutbox(instantOutbox)

    then:
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
      items.size() * executor.execute(_) >> { Runnable runnable ->
        assert runnable instanceof OutboxProcessingHost
      }
      1 * monitorLocksProvider.release()
      0 * _
  }

  def "Should reset the outbox item when monitor is called and the executor rejects the tasks"() {
    given:
      def now = Instant.now(clock)

    and:
      def itemBuilder = OutboxItemBuilder.make().withStatus(OutboxStatus.PENDING)
      def pendingItem = itemBuilder.build()
      def originalItem = itemBuilder.build() // Create a clone to use it for comparisons.

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
        assert item == originalItem.with {
          status = OutboxStatus.RUNNING
          lastExecution = now
          rerunAfter = now + DURATION_ONE_HOUR
          it
        }
        return item
      }
      1 * executor.execute(_) >> { throw new RejectedExecutionException() }
      1 * store.update(_) >> { OutboxItem item ->
        assert item == originalItem.with {
          status = OutboxStatus.PENDING
          // It seems that if the assertion fails (e.g. if the following line is uncommented), the test will still pass.
          // This might be due to the `onFailure` block inside the `monitor` method.
          // nextRun = now + DURATION_ONE_HOUR + DURATION_ONE_HOUR
          rerunAfter = null
          it
        }
        return item
      }
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
