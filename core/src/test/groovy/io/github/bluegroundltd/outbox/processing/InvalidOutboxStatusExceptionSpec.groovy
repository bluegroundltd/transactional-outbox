package io.github.bluegroundltd.outbox.processing

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

class InvalidOutboxStatusExceptionSpec extends Specification {
  def "Should build an [InvalidOutboxStatusException] with default values"() {
    given:
      def outboxItem = OutboxItemBuilder.make().build()
      def expectedMessage = outboxItem.with {
        "Invalid status: ${it.status} for outbox: ${it.id}."
      }

    when:
      def exception = new InvalidOutboxStatusException(outboxItem)

    then:
      exception.message == expectedMessage
      exception.cause == null
      0 * _
  }

  def "Should build an [InvalidOutboxStatusException] with expected statuses"() {
    given:
      def outboxItem = OutboxItemBuilder.make().build()
      def expectedStatues = (OutboxStatus.values() - outboxItem.status).toList().toSet()

    and:
      def expectedMessage = outboxItem.with {
        "Invalid status: ${it.status} for outbox: ${it.id}. Expected one of: ${expectedStatues.join(", ")}."
      }

    when:
      def exception = new InvalidOutboxStatusException(outboxItem, expectedStatues)

    then:
      exception.message == expectedMessage
      exception.cause == null
      0 * _
  }

  def "Should build an [InvalidOutboxStatusException] with the supplied values"() {
    given:
      def outboxItem = GroovyMock(OutboxItem)
      def expectedStatues = OutboxStatus.values().toList().toSet()
      def message = "Exception Message"
      def cause = Mock(Throwable)

    when:
      def exception = new InvalidOutboxStatusException(outboxItem, expectedStatues, message, cause)

    then:
      exception.message == message
      exception.cause == cause
      0 * _
  }
}
