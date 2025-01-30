package io.github.bluegroundltd.springoutbox.event

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.springoutbox.TransactionalOutbox
import io.github.bluegroundltd.springoutbox.utils.OutboxItemBuilder
import spock.lang.Specification

class InstantOutboxProcessorSpec extends Specification {
  TransactionalOutbox sprintTransactionalOutbox
  InstantOutboxProcessor instantOutboxProcessor

  def setup() {
    sprintTransactionalOutbox = Mock()
    instantOutboxProcessor = new InstantOutboxProcessor(sprintTransactionalOutbox)
  }

  def "Should process an outbox item by monitoring its ID"() {
    given:
      OutboxItem outboxItem = OutboxItemBuilder.make().build()

    when:
      instantOutboxProcessor.processInstantOutbox(outboxItem)

    then:
      1 * sprintTransactionalOutbox.monitor(outboxItem.id)
      0 * _
  }
}
