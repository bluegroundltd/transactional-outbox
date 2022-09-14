package com.blueground.outbox

import com.blueground.outbox.item.OutboxItem
import com.blueground.outbox.item.OutboxPayload
import com.blueground.outbox.item.OutboxStatus
import com.blueground.outbox.item.OutboxType
import com.blueground.outbox.store.OutboxFilter
import com.blueground.outbox.store.OutboxStore
import com.blueground.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ExecutorService

class TransactionalOutboxImplSpec extends Specification {
  private static final long LOCK_IDENTIFIER = 1L
  Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  Map<OutboxType, OutboxHandler> handlers = Mock()
  OutboxLocksProvider locksProvider = Mock()
  OutboxStore store = Mock()
  ExecutorService executor = Mock()
  TransactionalOutbox transactionalOutbox

  def setup() {
    transactionalOutbox = new TransactionalOutboxImpl(clock, handlers, LOCK_IDENTIFIER, locksProvider, store, executor)
  }

  def "Should throw UnsupportedOperationException when item isn't supported"() {
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)

    when:
      transactionalOutbox.add(type, payload)

    then:
      1 * handlers.get(type) >> null
      2 * type.getType() >> "type"
      0 * _

    and:
      thrown(UnsupportedOperationException)
  }

  def "Should delegate to outbox store when add is called"() {
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)
      def handler = GroovyMock(OutboxHandler)

    and:
      def serializedPayload = "serializedPayload"
      def nextRun = GroovyMock(Instant)

    and:
      def expectedOutboxItem =
        OutboxItemBuilder.make().withType(type).withPayload(serializedPayload).withNextRun(nextRun).build()

    when:
      transactionalOutbox.add(type, payload)

    then:
      1 * type.getType() >> "type"
      1 * handlers.get(type) >> handler
      1 * handler.serialize(payload) >> serializedPayload
      1 * handler.getNextExecutionTime(0) >> nextRun
      1 * store.insert(_) >> { OutboxItem item ->
        with(item) {
          id == null
          type == expectedOutboxItem.type
          status == expectedOutboxItem.status
          item.payload == expectedOutboxItem.payload
          retries == expectedOutboxItem.retries
          nextRun == expectedOutboxItem.nextRun
          lastExecution == expectedOutboxItem.lastExecution
          rerunAfter == expectedOutboxItem.rerunAfter
        }
      }
      0 * _
  }

  def "Should delegate to the executor thread pool when monitor is called"() {
    given:
      def pendingItem = OutboxItemBuilder.makePending()
      def runningItem = OutboxItemBuilder.make().withStatus(OutboxStatus.RUNNING).build()
      def items = [pendingItem, runningItem]

    and:
      def expectedHandler = GroovyMock(OutboxHandler)
      def now = Instant.now(clock)

    when:
      transactionalOutbox.monitor()

    then:
      1 * locksProvider.acquire(LOCK_IDENTIFIER)
      1 * store.fetch(_) >> { OutboxFilter filter ->
        with (filter) {
          outboxPendingFilter.nextRunLessThan == now
          outboxRunningFilter.rerunAfterGreaterThan == now
        }
        items
      }
      items.size() * store.update(_) >> { OutboxItem item ->
        with(item) {
          item.status == OutboxStatus.RUNNING
          item.lastExecution == now
          // TODO verify nextRun (maybe pass it via constructor)
        }
      }
      1 * locksProvider.release(LOCK_IDENTIFIER)
      items.size() * handlers.get(_) >> expectedHandler
      items.size() * executor.execute(_)
      0 * _
  }
}
