package com.blueground.outbox

import com.blueground.outbox.item.OutboxItem
import com.blueground.outbox.item.OutboxStatus
import com.blueground.outbox.item.OutboxType
import com.blueground.outbox.store.OutboxStore
import com.blueground.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class OutboxItemProcessorSpec extends Specification {
  Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  def item = OutboxItemBuilder.makeRunning()
  def unsupportedOutboxType = GroovyMock(OutboxType)
  OutboxHandler handler = GroovyMock()
  OutboxStore store = GroovyMock()
  OutboxItemProcessor processor

  def setup() {
    processor = new OutboxItemProcessor(
      item,
      handler,
      store
    )
  }

  def "Should don nothing when an erroneous item type is provided"() {
    when:
      processor.run()

    then:
      1 * handler.getSupportedType() >> unsupportedOutboxType
      0 * _
  }

  def "Should handle an item and update its status to completion when run is called"() {
    when:
      processor.run()

    then:
      1 * handler.getSupportedType() >> item.type
      1 * handler.handle(item.payload)
      1 * store.update(_) >> { OutboxItem item ->
        assert item.status == OutboxStatus.COMPLETED
      }
      0 * _
  }

  def "Should gracefully handle a failure during handling with max retries"() {
    when:
      processor.run()

    then:
      1 * handler.getSupportedType() >> item.type
      1 * handler.handle(item.payload) >> { throw new Exception() }
      1 * handler.hasReachedMaxRetries(_) >> true
      1 * handler.handleFailure(item.payload)
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
      1 * handler.getSupportedType() >> item.type
      1 * handler.handle(item.payload) >> { throw new Exception() }
      1 * handler.hasReachedMaxRetries(_) >> false
      1 * handler.getNextExecutionTime(_) >> expectedNextRun
      1 * store.update(_) >> { OutboxItem item ->
        with (item) {
          status == OutboxStatus.PENDING
          retries == 1
          nextRun == expectedNextRun
        }
      }
      0 * _
  }
}
