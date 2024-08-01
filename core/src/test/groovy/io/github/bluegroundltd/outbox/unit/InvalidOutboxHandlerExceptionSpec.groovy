package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.InvalidOutboxHandlerException
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

class InvalidOutboxHandlerExceptionSpec extends Specification {
  def "Should build an [InvalidOutboxHandlerException] with default values"() {
    given:
      def outboxItem = OutboxItemBuilder.make().build()
      def expectedMessage = outboxItem.with {
        "Invalid Outbox Handler for item with id: ${it.id} and type: ${it.type}"
      }

    when:
      def exception = new InvalidOutboxHandlerException(outboxItem)

    then:
      exception.message == expectedMessage
      exception.cause == null
      0 * _
  }

  def "Should build an [InvalidOutboxHandlerException] with the supplied values"() {
    given:
      def outboxItem = GroovyMock(OutboxItem)
      def message = "Exception Message"
      def cause = Mock(Throwable)

    when:
      def exception = new InvalidOutboxHandlerException(outboxItem, message, cause)

    then:
      exception.message == message
      exception.cause == cause
      0 * _
  }
}
