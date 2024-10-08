package io.github.bluegroundltd.outbox.processing

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class OutboxItemProcessorSpec extends Specification {
  private Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  private Instant now = Instant.now(clock)

  private itemBuilder = OutboxItemBuilder.makePending(now)
  private processedItem = itemBuilder.buildAndPrepareForProcessing(now)
  private originalItem = itemBuilder.buildAndPrepareForProcessing(now) // Create an identical second to use it for comparisons.

  private OutboxHandler handler = Mock()
  private def handlerResolver = { OutboxItem item -> handler }
  private OutboxStore store = GroovyMock()

  private OutboxItemProcessor processor

  def setup() {
    processor = new OutboxItemProcessor(
      processedItem,
      handlerResolver,
      store,
      clock
    )
  }

  def "Should throw [InvalidOutboxStateException] when the outbox is not marked for processing"() {
    given: "An outbox item that should not be processed due to a future next run"
      def outboxItem = OutboxItemBuilder
        .makePending(now.plusSeconds(1000))
        .build()
        .with {
          prepareForProcessing(now, now.plusSeconds(1000))
          it
        }
      def itemProcessor = new OutboxItemProcessor(
        outboxItem,
        { null },
        store,
        clock
      )

    and:
      def expectedMessage = outboxItem.with {
        "Outbox item with id: ${it.id} is not marked for processing."
      }
      def expectedException = new InvalidOutboxStateException(outboxItem, expectedMessage)

    when:
      itemProcessor.run()

    then:
      0 * _

    and:
      def ex = thrown(InvalidOutboxStateException)
      ex == expectedException
  }

  def "Should throw [InvalidOutboxHandlerException] when a handler cannot be resolved"() {
    given:
      def itemProcessor = new OutboxItemProcessor(
        processedItem,
        { null },
        store,
        clock
      )

    and:
      def expectedException = new InvalidOutboxHandlerException(
        processedItem,
        "Handler could not be resolved for item with id: ${processedItem.id} and type: ${processedItem.type}"
      )

    when:
      itemProcessor.run()

    then:
      0 * _

    and:
      def ex = thrown(InvalidOutboxHandlerException)
      ex == expectedException
  }

  def "Should throw [InvalidOutboxHandlerException] when an erroneous item type is provided"() {
    given:
      def expectedException = new InvalidOutboxHandlerException(
        processedItem,
        "Handler ${handler.class} does not support item of type: ${processedItem.type}"
      )

    when:
      processor.run()

    then:
      1 * handler.getSupportedType() >> Mock(OutboxType)
      0 * _

    and:
      def ex = thrown(InvalidOutboxHandlerException)
      ex == expectedException
  }

  def "Should handle an item and update its status to 'COMPLETED' when [run] is called"() {
    given:
      def retentionDuration = Duration.ofDays(10)

    when:
      processor.run()

    then:
      1 * handler.getSupportedType() >> processedItem.type
      1 * handler.handle(processedItem.payload)
      1 * handler.getRetentionDuration() >> retentionDuration
      1 * store.update(_) >> { OutboxItem item ->
        assert item == originalItem.with {
          status = OutboxStatus.COMPLETED
          deleteAfter = Instant.now(clock) + retentionDuration
          it
        }
      }
      0 * _
  }

  def "Should gracefully handle a processing failure when it has reached the max number of retries"() {
    given:
      def caughtException = new RuntimeException()

    when:
      processor.run()

    then:
      1 * handler.getSupportedType() >> processedItem.type
      1 * handler.handle(processedItem.payload) >> { throw caughtException }
      1 * handler.hasReachedMaxRetries(processedItem.retries) >> true
      1 * handler.handleFailure(processedItem.payload)
      1 * store.update(_) >> { OutboxItem item ->
        assert item == originalItem.with {
          status = OutboxStatus.FAILED
          it
        }
      }
      0 * _

    and:
      def ex = thrown(OutboxHandlerException)
      ex.cause == caughtException
  }

  def "Should gracefully handle a processing failure when it hasn't reached the max number of retries"() {
    given:
      def expectedNextRun = Instant.now(clock)
      def caughtException = new RuntimeException()

    when:
      processor.run()

    then:
      1 * handler.getSupportedType() >> processedItem.type
      1 * handler.handle(processedItem.payload) >> { throw caughtException }
      1 * handler.hasReachedMaxRetries(processedItem.retries) >> false
      1 * handler.getNextExecutionTime(processedItem.retries) >> expectedNextRun
      1 * store.update(_) >> { OutboxItem item ->
        assert item == originalItem.with {
          status = OutboxStatus.PENDING
          retries = originalItem.retries + 1
          nextRun = expectedNextRun
          it
        }
      }
      0 * _

    and:
      def ex = thrown(OutboxHandlerException)
      ex.cause == caughtException
  }

  def "Should set item status to 'PENDING' when [reset] is invoked"() {
    when:
      processor.reset()

    then:
      1 * store.update(_) >> { OutboxItem item ->
        assert item == originalItem.with {
          status = OutboxStatus.PENDING
          rerunAfter = null
          it
        }
      }
      0 * _
  }

  def "Should do nothing when [reset] is invoked and item status is #itemStatus"() {
    given:
      def itemProcessor = new OutboxItemProcessor(
        itemBuilder.withStatus(itemStatus).build(),
        handlerResolver,
        store,
        clock
      )

    when:
      itemProcessor.reset()

    then:
      0 * _

    where:
      itemStatus << (OutboxStatus.values() - OutboxStatus.RUNNING).toList()
  }

  private static OutboxItem updateOutboxForProcessing(OutboxItem item, Instant monitorTimestamp) {
    item.prepareForProcessing(monitorTimestamp, monitorTimestamp)
    return item
  }
}
