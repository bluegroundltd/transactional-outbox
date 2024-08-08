package io.github.bluegroundltd.outbox.processing

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxItemGroup
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class OutboxGroupProcessorSpec extends Specification {
  private final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  private final Instant now = Instant.now(clock)

  private final outboxItems = (1..5).collect {
    OutboxItemBuilder.make().withStatus(OutboxStatus.RUNNING).build()
  }
  private final Map<Long, OutboxHandler> outboxHandlers = outboxItems.collectEntries {
    [(it.id): Mock(OutboxHandler)]
  }
  private final outboxItemGroup = new OutboxItemGroup(outboxItems)

  private Long failingItemId = null // Allows for dynamically setting a handler resolution to fail.
  private final handlerResolutionException = new RuntimeException("Handler could not be resolved")
  private final handlerResolver = { OutboxItem item ->
    if (item.id == failingItemId) {
      throw handlerResolutionException
    }
    return outboxHandlers[item.id]
  }
  private final OutboxStore store = GroovyMock()

  private final OutboxGroupProcessor processor = new OutboxGroupProcessor(
    outboxItemGroup,
    handlerResolver,
    store,
    clock
  )

  def "Should process all items in the group when [run] is invoked"() {
    when:
      processor.run()

    then:
      outboxItems.each {
        def handler = outboxHandlers[it.id]
        def retentionDuration = Duration.ofDays(10)

        1 * handler.getSupportedType() >> it.type
        1 * handler.handle(it.payload)
        1 * handler.getRetentionDuration() >> retentionDuration
        1 * store.update(_) >> { OutboxItem item ->
          // For proper verification see [OutboxItemProcessorSpec].
          assert item.id == it.id
          item
        }
      }
      0 * _
  }

  def "Should propagate any exception thrown when processing items"() {
    given:
      def successfullyProcessedCount = 2
      def processedItems = outboxItems.take(successfullyProcessedCount)
      def failingItem = outboxItems[successfullyProcessedCount]
      def notProcessedItems = outboxItems - processedItems - failingItem

    and: "Setting up handler resolution failure"
      failingItemId = failingItem.id

    when:
      processor.run()

    then:
      processedItems.each {
        def handler = outboxHandlers[it.id]

        1 * handler.getSupportedType() >> it.type
        1 * handler.handle(it.payload)
        1 * handler.getRetentionDuration() >> Duration.ofDays(10)
        1 * store.update(_) >> { OutboxItem item ->
          // For proper verification see [OutboxItemProcessorSpec].
          assert item.id == it.id
          item
        }
      }
      0 * _

    and:
      def ex = thrown(RuntimeException)
      ex == handlerResolutionException

    and:
      processedItems.each {
        assert it.status == OutboxStatus.COMPLETED
      }
      assert failingItem.status == OutboxStatus.RUNNING // The exception was thrown before it's status was updated.
      notProcessedItems.each {
        assert it.status == OutboxStatus.RUNNING
      }
  }

  def "Should reset all items in the group when [reset] is invoked"() {
    when:
      processor.reset()

    then:
      outboxItems.each {
        1 * store.update(_) >> { OutboxItem item ->
          // For proper verification see [OutboxItemProcessorSpec].
          assert item.id == it.id
          item
        }
      }
      0 * _

    and:
      outboxItems.each {
        assert it.status == OutboxStatus.PENDING
      }
  }

  def "Should propagate any exception thrown when resetting items"() {
    given:
      def successfullyResetCount = 2
      def resetItems = outboxItems.take(successfullyResetCount)
      def failingItem = outboxItems[successfullyResetCount]
      def notResetItems = outboxItems - resetItems - failingItem

    and:
      def exception = new RuntimeException("Reset failed")

    when:
      processor.reset()

    then:
      resetItems.each {
        1 * store.update(_) >> { OutboxItem item ->
          // For proper verification see [OutboxItemProcessorSpec].
          assert item.id == it.id
          item
        }
      }
      1 * store.update(_) >> { OutboxItem item ->
        // For proper verification see [OutboxItemProcessorSpec].
        assert item.id == failingItem.id
        throw exception
      }
      0 * _

    and:
      def ex = thrown(RuntimeException)
      ex == exception

    and:
      resetItems.each {
        assert it.status == OutboxStatus.PENDING
      }
      failingItem.status == OutboxStatus.PENDING // It's status was updated before the exception was thrown.
      notResetItems.each {
        assert it.status == OutboxStatus.RUNNING
      }
  }
}
