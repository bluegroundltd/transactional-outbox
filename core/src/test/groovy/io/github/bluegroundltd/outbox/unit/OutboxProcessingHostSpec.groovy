package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.OutboxItemProcessor
import io.github.bluegroundltd.outbox.OutboxItemProcessorDecorator
import io.github.bluegroundltd.outbox.OutboxProcessingHost
import spock.lang.Specification

class OutboxProcessingHostSpec extends Specification {
  private OutboxItemProcessor processor = Mock()

  // By default use a no-decorators host to simplify the tests.
  // There is a stand-alone test for the decorator logic.
  private OutboxProcessingHost processingHost = new OutboxProcessingHost(processor, [])

  def "Should decorate the processor with the decorators when being instantiated"() {
    given:
      def decorators = [
        Mock(OutboxItemProcessorDecorator),
        Mock(OutboxItemProcessorDecorator)
      ]
      def runnables = decorators.collect { Mock(Runnable) }

    when:
      def processingHostWithDecorator = new OutboxProcessingHost(processor, decorators)

    then:
      1 * decorators[0].decorate(processor) >> runnables[0]
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
      1 * processor.run()
      0 * _

    and:
      noExceptionThrown()
  }

  def "Should delegate to the processor when [reset] is called"() {
    when:
      processingHost.reset()

    then:
      1 * processor.reset()
      0 * _

    and:
      noExceptionThrown()
  }
}
