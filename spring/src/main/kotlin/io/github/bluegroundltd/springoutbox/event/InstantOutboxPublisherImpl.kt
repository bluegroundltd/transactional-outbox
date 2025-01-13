package io.github.bluegroundltd.springoutbox.event

import io.github.bluegroundltd.outbox.event.InstantOutboxEvent
import io.github.bluegroundltd.outbox.event.InstantOutboxPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
internal class InstantOutboxPublisherImpl(
  private val applicationEventPublisher: ApplicationEventPublisher,
) : InstantOutboxPublisher {

  @Transactional(propagation = Propagation.MANDATORY)
  override fun publish(event: InstantOutboxEvent) =
    applicationEventPublisher.publishEvent(
      AfterCommitApplicationEvent(
        source = this,
        outboxItem = event.outbox
      )
    )
}
