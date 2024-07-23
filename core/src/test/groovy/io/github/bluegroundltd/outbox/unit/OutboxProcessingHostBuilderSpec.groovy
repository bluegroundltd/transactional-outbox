package io.github.bluegroundltd.outbox.unit

import io.github.bluegroundltd.outbox.OutboxProcessingAction
import io.github.bluegroundltd.outbox.OutboxProcessingHostBuilder
import spock.lang.Specification

class OutboxProcessingHostBuilderSpec extends Specification {
  private OutboxProcessingHostBuilder builder = new OutboxProcessingHostBuilder()

  def "Should build an [OutboxProcessingHost] when [build] is invoked"() {
    given:
      def processingAction = Mock(OutboxProcessingAction)
      // We use an empty decorator list to simplify the test.
      // There is a stand-alone test for the decorator logic in [OutboxProcessingHostSpec].
      def decorators = []

    when:
      def result = builder.build(processingAction, decorators)

    then:
      0 * _

    and:
      result != null
  }
}
