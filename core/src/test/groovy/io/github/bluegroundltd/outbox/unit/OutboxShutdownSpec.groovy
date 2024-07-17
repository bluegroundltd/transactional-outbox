package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxItemProcessor
import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
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
  private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneId.systemDefault())

  private Map<OutboxType, OutboxHandler> handlers = [:]
  private OutboxLocksProvider monitorLocksProvider = Mock()
  private OutboxLocksProvider cleanupLocksProvider = Mock()
  private OutboxStore store = Mock()
  private InstantOutboxPublisher instantOutboxPublisher = Mock()
  private OutboxItemFactory outboxItemFactory = Mock()
  private ExecutorService executor = Mock()
  private Duration threadPoolTimeOut = Duration.ofMillis(5000)

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
      executor,
      [],
      threadPoolTimeOut
    )
  }

  def "Should delegate to the outbox store when shutdown is called and the timeout elapsed before termination"() {
    given:
      // Can't mock OutboxItemProcessor (because it's final) so we need an actual implementation.
      def itemBuilder = OutboxItemBuilder.make()
      def runningItem = itemBuilder.withStatus(OutboxStatus.RUNNING).withRerunAfter().build()
      def resetItem = itemBuilder.withStatus(OutboxStatus.PENDING).withoutRerunAfter().build()
      def handler = GroovyMock(OutboxHandler)
      def processor = new OutboxItemProcessor(runningItem, handler, store, CLOCK)
      def expected = [processor]

    when:
      transactionalOutbox.shutdown()

    then:
      1 * executor.shutdown()
      1 * executor.awaitTermination(threadPoolTimeOut.toSeconds(), TimeUnit.SECONDS) >> false
      1 * executor.shutdownNow() >> expected
      1 * store.update(resetItem)
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
