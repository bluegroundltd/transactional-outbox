package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.OutboxLocksProvider
import io.github.bluegroundltd.outbox.TransactionalOutbox
import io.github.bluegroundltd.outbox.TransactionalOutboxImpl
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ExecutorService

class OutboxAddSpec extends Specification {
  private static final Duration DURATION_ONE_HOUR = Duration.ofHours(1)
  Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  Map<OutboxType, OutboxHandler> handlers = Mock()
  OutboxLocksProvider locksProvider = Mock()
  OutboxStore store = Mock()
  ExecutorService executor = Mock()
  Duration threadPoolTimeOut = Duration.ofMillis(5000)
  TransactionalOutbox transactionalOutbox

  def setup() {
    transactionalOutbox = new TransactionalOutboxImpl(
      clock,
      handlers,
      locksProvider,
      store,
      DURATION_ONE_HOUR,
      executor,
      threadPoolTimeOut
    )
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
}
