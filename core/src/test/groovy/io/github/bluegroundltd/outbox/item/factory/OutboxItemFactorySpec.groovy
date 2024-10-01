package io.github.bluegroundltd.outbox.item.factory

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.grouping.OutboxGroupIdProvider
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.outbox.utils.SpecHelper
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class OutboxItemFactorySpec extends Specification implements SpecHelper {
  private Duration duration
  private Clock clock
  private Map<OutboxType, OutboxHandler> handlers
  private OutboxGroupIdProvider groupIdProvider

  private OutboxItemFactory outboxItemFactory

  def setup() {
    duration = Duration.ofHours(1)
    clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    handlers = Mock()
    groupIdProvider = Mock()

    outboxItemFactory = new OutboxItemFactory(
      handlers,
      groupIdProvider
    )
  }

  def "Should make an outbox to be processed by a scheduled job"() {
    given:
      def payload = GroovyMock(OutboxPayload)
      def type = GroovyMock(OutboxType)

    and:
      def handler = GroovyMock(OutboxHandler)
      def serializedPayload = "serializedPayload"
      def nextRun = Instant.now(clock)
      def groupId = generateString()

    when:
      OutboxItem result = outboxItemFactory.makeScheduledOutboxItem(type, payload)

    then:
      1 * handlers.get(type) >> handler
      1 * handler.serialize(payload) >> serializedPayload
      1 * handler.getNextExecutionTime(0) >> nextRun
      1 * groupIdProvider.execute(type, payload) >> groupId
      0 * _

    and:
      !result.id
      result.type == type
      result.status == OutboxStatus.PENDING
      result.payload == serializedPayload
      result.retries == 0
      result.nextRun == nextRun.minusMillis(1)
      !result.lastExecution
      !result.rerunAfter
      !result.deleteAfter
      result.groupId == groupId
  }
}
