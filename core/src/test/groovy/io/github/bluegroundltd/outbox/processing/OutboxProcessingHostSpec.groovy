package io.github.bluegroundltd.outbox.processing

import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

class OutboxProcessingHostSpec extends Specification {
  private OutboxProcessingAction processingAction = Mock()

  // By default use a no-decorators host to simplify the tests.
  // There is a stand-alone test for the decorator logic.
  private OutboxProcessingHost processingHost = new OutboxProcessingHost(processingAction, [])

  def "Should decorate the processor with the decorators when being instantiated"() {
    given:
      def decorators = [
        Mock(OutboxItemProcessorDecorator),
        Mock(OutboxItemProcessorDecorator)
      ]
      def runnables = decorators.collect { Mock(Runnable) }

    when:
      def processingHostWithDecorator = new OutboxProcessingHost(processingAction, decorators)

    then:
      1 * decorators[0].decorate(_) >> {
        assert it != null
        return runnables[0]
      }
      1 * decorators[1].decorate(runnables[0]) >> runnables[1]
      0 * _

    and:
      processingHostWithDecorator != null
      noExceptionThrown()
  }

  def "Should delegate to the processor when [run] is called"() {
    when:
      processingHost.run()

    then:
      1 * processingAction.run()
      0 * _

    and:
      noExceptionThrown()
  }

  def "Should catch #exceptionType that occurs while processing and reset the processor"() {
    when:
      processingHost.run()

    then:
      1 * processingAction.run() >> { throw new Exception("Processing Exception") }
      1 * processingAction.reset()
      0 * _

    and:
      noExceptionThrown()

      // The only difference between the cases is the logging which is not really testable.
      // However, they are all included for coverage.
    where:
      exceptionType               | exception
      "a handler exception"       | makeHandlerException()
      "an outbox state exception" | makeOutboxStateException()
      "a non-handler exception"   | new Exception("Processing Exception")
  }

  def "Should delegate to the processor when [reset] is called"() {
    when:
      processingHost.reset()

    then:
      1 * processingAction.reset()
      0 * _

    and:
      noExceptionThrown()
  }

  private static OutboxHandlerException makeHandlerException() {
    def outboxItem = OutboxItemBuilder.make().build()
    def cause = new RuntimeException("Exception Message")
    return new OutboxHandlerException(outboxItem, cause)
  }

  private static InvalidOutboxStateException makeOutboxStateException() {
    def outboxItem = OutboxItemBuilder.make().build()
    return new InvalidOutboxStateException(outboxItem)
  }
}
