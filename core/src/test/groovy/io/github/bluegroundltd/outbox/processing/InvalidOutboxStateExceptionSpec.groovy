package io.github.bluegroundltd.outbox.processing

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

class InvalidOutboxStateExceptionSpec extends Specification {
  def "Should build an [InvalidOutboxStatException] with default values"() {
    given:
      def outboxItem = OutboxItemBuilder.make().build()
      def expectedMessage = outboxItem.with {
        "Invalid state for outbox: ${it.id}."
      }

    when:
      def exception = new InvalidOutboxStateException(outboxItem)

    then:
      exception.message == expectedMessage
      exception.cause == null
      0 * _
  }

  def "Should build an [InvalidOutboxStateException] with the supplied values"() {
    given:
      def outboxItem = GroovyMock(OutboxItem)
      def message = "Exception Message"
      def cause = Mock(Throwable)

    when:
      def exception = new InvalidOutboxStateException(outboxItem, message, cause)

    then:
      exception.message == message
      exception.cause == cause
      0 * _
  }
}
