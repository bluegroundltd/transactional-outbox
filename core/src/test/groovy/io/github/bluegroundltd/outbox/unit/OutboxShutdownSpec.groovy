package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxItemProcessor
import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.item.factory.OutboxItemFactory
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class OutboxShutdownSpec extends Specification {
  private static final Duration DURATION_ONE_HOUR = Duration.ofHours(1)
  Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  Map<OutboxType, OutboxHandler> handlers = Mock()
  OutboxLocksProvider locksProvider = Mock()
  OutboxStore store = Mock()
  InstantOutboxPublisher instantOutboxPublisher = Mock()
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
      instantOutboxPublisher,
      outboxItemFactory,
      DURATION_ONE_HOUR,
      executor,
      [],
      threadPoolTimeOut
    )
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

    then:
      1 * executor.shutdown()
      1 * executor.awaitTermination(threadPoolTimeOut.toSeconds(), TimeUnit.SECONDS) >> true

    when:
      transactionalOutbox.shutdown()

    then:
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
