package io.github.bluegroundltd.outbox.processing

import spock.lang.Specification

class OutboxProcessingHostComposerSpec extends Specification {
  private OutboxProcessingHostComposer composer = new OutboxProcessingHostComposer()

  def "Should build an [OutboxProcessingHost] when [compose] is invoked"() {
    given:
      def processingAction = Mock(OutboxProcessingAction)
      // We use an empty decorator list to simplify the test.
      // There is a stand-alone test for the decorator logic in [OutboxProcessingHostSpec].
      def decorators = []

    when:
      def result = composer.compose(processingAction, decorators)

    then:
      0 * _

    and:
      result != null
  }
}
