package com.blueground.outbox

import com.blueground.outbox.item.OutboxItem
import com.blueground.outbox.store.OutboxStore
import spock.lang.Specification

import java.time.Instant

class OutboxItemProcessorSpec extends Specification {
  OutboxItem item = GroovyMock()
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

  def "Should handle an item and update it when run is called"() {
    when:
      processor.run()

    then:
      1 * handler.supports(item.type) >> true
      1 * handler.handle(item.payload)
      1 * store.update(item)
      0 * _
  }

  def "Should gracefully handle a failure during handling with max retries"() {
    when:
      processor.run()

    then:
      1 * handler.supports(item.type) >> true
      1 * handler.handle(item.payload) >> { throw new Exception() }
      1 * handler.hasReachedMaxRetries(_) >> true
      1 * handler.handleFailure(item.payload)
      1 * store.update(item)
      0 * _
  }

  def "Should gracefully handle a failure during handling with no max retries"() {
    when:
      processor.run()

    then:
      1 * handler.supports(item.type) >> true
      1 * handler.handle(item.payload) >> { throw new Exception() }
      1 * handler.hasReachedMaxRetries(_) >> false
      1 * handler.getNextExecutionTime(_) >> GroovyMock(Instant)
      1 * store.update(item)
      0 * _
  }
}
