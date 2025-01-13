package io.github.bluegroundltd.springoutbox.event

import io.github.bluegroundltd.outbox.event.InstantOutboxEvent
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import io.github.bluegroundltd.outbox.item.OutboxItem
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

class InstantOutboxPublisherImplSpec extends Specification {
  ApplicationEventPublisher applicationEventPublisher
  InstantOutboxPublisher instantOutboxPublisher

  def setup() {
    applicationEventPublisher = Mock()
    instantOutboxPublisher = new InstantOutboxPublisherImpl(applicationEventPublisher)
  }

  def "Should delegate to the application event publisher"() {
    given:
      InstantOutboxEvent instantOutboxEvent = new InstantOutboxEvent(GroovyMock(OutboxItem))

    when:
      instantOutboxPublisher.publish(instantOutboxEvent)

    then:
      applicationEventPublisher.publishEvent({
          it.outboxItem == instantOutboxEvent.outbox
      })
  }
}
