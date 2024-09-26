package io.github.bluegroundltd.outbox.processing

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

class OutboxHandlerExceptionSpec extends Specification {
  def "Should build an [OutboxHandlerException] with default values"() {
    given:
      def outboxItem = OutboxItemBuilder.make().build()
      def cause = Mock(Throwable)
      def expectedMessage = outboxItem.with {
        "Handler for outbox: ${it.id} failed${expectedCauseMessage}."
      }

    when:
      def exception = new OutboxHandlerException(outboxItem, cause)

    then:
      1 * cause.getMessage() >> causeMessage
      0 * _

    and:
      exception.cause == cause
      exception.message == expectedMessage
      0 * _

    where:
      causeMessage        || expectedCauseMessage
      "Exception Message" || " with message: 'Exception Message'"
      null                || ""
  }

  def "Should build an [OutboxHandlerException] with the supplied values"() {
    given:
      def outboxItem = GroovyMock(OutboxItem)
      def cause = Mock(Throwable)
      def message = "Exception Message"

    when:
      def exception = new OutboxHandlerException(outboxItem, cause, message)

    then:
      0 * _

    and:
      exception.message == message
      exception.cause == cause
  }
}
