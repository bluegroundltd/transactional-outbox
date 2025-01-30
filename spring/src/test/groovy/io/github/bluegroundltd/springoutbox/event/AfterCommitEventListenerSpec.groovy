package io.github.bluegroundltd.springoutbox.event

import io.github.bluegroundltd.outbox.item.OutboxItem
import spock.lang.Specification

class AfterCommitEventListenerSpec extends Specification {
  InstantOutboxProcessor instantOutboxProcessor = Mock()
  AfterCommitEventListener afterCommitEventListener

  def setup() {
    afterCommitEventListener = new AfterCommitEventListener(instantOutboxProcessor)
  }

  def "Should delegate to the transaction outbox to process the event"() {
    given:
      AfterCommitApplicationEvent event = new AfterCommitApplicationEvent(
        GroovyMock(Object),
        GroovyMock(OutboxItem)
      )

    when:
      afterCommitEventListener.handle(event)

    then:
      instantOutboxProcessor.processInstantOutbox(event.outboxItem)
  }
}
