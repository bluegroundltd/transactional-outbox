package io.github.bluegroundltd.outbox.item.factory

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class OutboxItemFactorySpec extends Specification {
  Duration duration
  Clock clock
  Map<OutboxType, OutboxHandler> handlers
  OutboxItemFactory outboxItemFactory

  def setup() {
    duration = Duration.ofHours(1)
    clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    handlers = Mock()
    outboxItemFactory = new OutboxItemFactory(
      clock,
      handlers,
      duration
    )
  }

  def "Should throw UnsupportedOperationException when item isn't supported"() {
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)

    when:
      outboxItemFactory.makeOnDemandOutboxItem(type, payload)

    then:
      1 * handlers.get(type)
      1 * type.getType() >> "type"
      0 * _

    and:
      thrown(UnsupportedOperationException)
  }

  def "Should make an outbox to be processed by a scheduled job"(){
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)

    and:
      def handler = GroovyMock(OutboxHandler)
      def serializedPayload = "serializedPayload"
      def nextRun = GroovyMock(Instant)

    when:
      OutboxItem result = outboxItemFactory.makeScheduledOutboxItem(type, payload)

    then:
      1 * handlers.get(type) >> handler
      1 * handler.serialize(payload) >> serializedPayload
      1 * handler.getNextExecutionTime(0) >> nextRun

    and:
      !result.id
      result.type == type
      result.status == OutboxStatus.PENDING
      result.payload == serializedPayload
      result.retries == 0
      result.nextRun == nextRun
      !result.lastExecution
      !result.rerunAfter
  }

  def "Should make an outbox to be processed on demand"(){
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)

    and:
      def handler = GroovyMock(OutboxHandler)
      def serializedPayload = "serializedPayload"
      def nextRun = GroovyMock(Instant)

    when:
      OutboxItem result = outboxItemFactory.makeOnDemandOutboxItem(type, payload)

    then:
      1 * handlers.get(type) >> handler
      1 * handler.serialize(payload) >> serializedPayload
      1 * handler.getNextExecutionTime(0) >> nextRun

    and:
      !result.id
      result.type == type
      result.status == OutboxStatus.RUNNING
      result.payload == serializedPayload
      result.retries == 0
      result.nextRun == nextRun
      result.lastExecution == Instant.now(clock)
      result.rerunAfter == Instant.now(clock).plus(duration)
  }

}
