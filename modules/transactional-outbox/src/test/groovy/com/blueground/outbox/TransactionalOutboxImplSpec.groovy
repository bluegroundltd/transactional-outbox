package com.blueground.outbox

import com.blueground.commons.test.UnitTestSpecification
import com.blueground.outbox.item.OutboxItem
import com.blueground.outbox.item.OutboxPayload
import com.blueground.outbox.item.OutboxStatus
import com.blueground.outbox.item.OutboxType
import com.blueground.outbox.store.OutboxFilter
import com.blueground.outbox.store.OutboxStore
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ExecutorService

class TransactionalOutboxImplSpec extends UnitTestSpecification {
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
      1 * type.getType()
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
      def expectedOutboxItem = new OutboxItem(
        null,
        type,
        OutboxStatus.PENDING,
        serializedPayload,
        0,
        nextRun,
        null,
        null
      )

    when:
      transactionalOutbox.add(type, payload)

    then:
      1 * handlers.get(type) >> handler
      1 * handler.serialize(payload) >> serializedPayload
      1 * handler.getNextExecutionTime(0) >> nextRun
      1 * store.insert(_) >> { OutboxItem item ->
        with(item) {
          id == expectedOutboxItem.id
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

  @Unroll
  def "Should delegate to the executor thread pool when monitor is called with batchSize #batchSize"() {
    given:
      def outboxItems = makeOutboxItemList(batchSize, OutboxStatus.PENDING)

    and:
      def handler = GroovyMock(OutboxHandler)
      def now = Instant.now(clock)

    when:
      transactionalOutbox.monitor()

    then:
      1 * locksProvider.acquire(LOCK_IDENTIFIER)
      1 * store.fetch(new OutboxFilter(now, now)) >> outboxItems
      batchSize * store.update(_)
      1 * locksProvider.release(LOCK_IDENTIFIER)
      batchSize * handlers.get(_) >> handler
      batchSize * executor.execute(_)
      0 * _

    where:
      batchSize << ([0, 1, 10] as Integer)
  }

  @Unroll
  def "Should throw an exception when an item of status #status is fetched into monitor"() {
    given:
      def outboxItems = makeOutboxItemList(1, status)

    when:
      transactionalOutbox.monitor()

    then:
      1 * locksProvider.acquire(_)
      1 * store.fetch(_) >> outboxItems

    and:
      thrown(IllegalArgumentException)

    where:
      status                 | _
      OutboxStatus.FAILED    | _
      OutboxStatus.COMPLETED | _
  }

  private def makeOutboxItem(OutboxStatus status) {
    return new OutboxItem(
      generateLong(),
      new OutboxType() {
        @Override
        String getType() {
          return generateString()
        }
      },
      status ?: randomEnum(OutboxStatus),
      generateString(),
      generateLong(),
      generateInstant(),
      generateInstant(),
      generateInstant()
    )
  }

  private def makeOutboxItemList(Integer length, OutboxStatus status) {
    def list = []
    for (_ in (0..<length)) {
      list.add(makeOutboxItem(status))
    }
    return list
  }
}
