package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxItemProcessor
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

  private itemBuilder = OutboxItemBuilder.make()
  private runningItem = itemBuilder.withStatus(OutboxStatus.RUNNING).build()
  private resetItem = itemBuilder.withStatus(OutboxStatus.PENDING).withoutRerunAfter().build()

  private unsupportedOutboxType = GroovyMock(OutboxType)
  private OutboxHandler handler = GroovyMock()
  private OutboxStore store = GroovyMock()

  private OutboxItemProcessor processor

  def setup() {
    processor = new OutboxItemProcessor(
      runningItem,
      handler,
      store,
      clock
    )
  }

  def "Should do nothing when an erroneous item type is provided"() {
    when:
      processor.run()

    then:
      1 * handler.getSupportedType() >> unsupportedOutboxType
      0 * _
  }

  def "Should handle an item and update its status to completion when run is called"() {
    given:
      def retentionDuration = Duration.ofDays(10)

    when:
      processor.run()

    then:
      1 * handler.getSupportedType() >> runningItem.type
      1 * handler.handle(runningItem.payload)
      1 * handler.getRetentionDuration() >> retentionDuration
      1 * store.update(_) >> { OutboxItem item ->
        assert item.status == OutboxStatus.COMPLETED
        assert item.deleteAfter == Instant.now(clock) + retentionDuration
      }
      0 * _
  }

  def "Should gracefully handle a failure during handling with max retries"() {
    when:
      processor.run()

    then:
      1 * handler.getSupportedType() >> runningItem.type
      1 * handler.handle(runningItem.payload) >> { throw new Exception() }
      1 * handler.hasReachedMaxRetries(_) >> true
      1 * handler.handleFailure(runningItem.payload)
      1 * store.update(_) >> { OutboxItem item ->
        assert item.status == OutboxStatus.FAILED
      }
      0 * _
  }

  def "Should gracefully handle a failure during handling with no max retries"() {
    given:
      def expectedNextRun = Instant.now(clock)

    when:
      processor.run()

    then:
      1 * handler.getSupportedType() >> runningItem.type
      1 * handler.handle(runningItem.payload) >> { throw new Exception() }
      1 * handler.hasReachedMaxRetries(_) >> false
      1 * handler.getNextExecutionTime(_) >> expectedNextRun
      1 * store.update(_) >> { OutboxItem item ->
        with(item) {
          status == OutboxStatus.PENDING
          retries == 1
          nextRun == expectedNextRun
        }
      }
      0 * _
  }

  def "Should stop processing and set item status to 'PENDING' when [reset] is invoked"() {
    when:
      processor.reset()

    then:
      1 * store.update(resetItem)
      0 * _

    when:
      processor.run()

    then:
      0 * _
  }

  def "Should stop processing and do nothing else when [reset] is invoked and item status is #itemStatus"() {
    given:
      def itemProcessor = new OutboxItemProcessor(
        itemBuilder.withStatus(itemStatus).build(),
        handler,
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
}
