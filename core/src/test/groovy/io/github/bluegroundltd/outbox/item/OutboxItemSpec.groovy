package io.github.bluegroundltd.outbox.item

import io.github.bluegroundltd.outbox.utils.OutboxItemBuilder
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class OutboxItemSpec extends Specification {
  private static final Instant NOW = Instant.now()
  private static final Instant BEFORE_NOW = NOW - Duration.ofSeconds(1)
  private static final Instant AFTER_NOW = NOW + Duration.ofSeconds(1)

  def "Should properly mark for processing PENDING and RUNNING items"() {
    given:
      def outboxItem = OutboxItemBuilder.make()
        .withStatus(status)
        .withNextRun(nextRun)
        .withRerunAfter(rerunAfter)
        .build()

    when:
      outboxItem.prepareForProcessing(NOW, NOW.plusSeconds(1000))

    then:
      0 * _

    and:
      outboxItem.markedForProcessing == eligible

    where:
      status               | nextRun    | rerunAfter || eligible
      OutboxStatus.PENDING | BEFORE_NOW | null       || true
      OutboxStatus.PENDING | AFTER_NOW  | null       || false
      OutboxStatus.RUNNING | BEFORE_NOW | BEFORE_NOW || true
      OutboxStatus.RUNNING | BEFORE_NOW | AFTER_NOW  || false
  }

  def "Should not mark the item for processing when status is #status"() {
    given:
      def outboxItem = OutboxItemBuilder.make()
        .withStatus(status)
        .withNextRun(BEFORE_NOW)
        .withRerunAfter(BEFORE_NOW)
        .build()

    when:
      outboxItem.prepareForProcessing(NOW, NOW.plusSeconds(1000))

    then:
      0 * _

    and:
      !outboxItem.markedForProcessing

    where:
      status << (OutboxStatus.values() - [OutboxStatus.PENDING, OutboxStatus.RUNNING]).toList()
  }

  def "Should update the relevant fields when 'prepareForProcessing' is called and the item is eligible for processing"() {
    given:
      def itemBuilder = OutboxItemBuilder.make()
        .withStatus(OutboxStatus.PENDING)
        .withNextRun(BEFORE_NOW)

      def outboxItem = itemBuilder.build()

    and:
      def rerunAfter = NOW.plusSeconds(1000)
      def expectedItem = itemBuilder
        .withStatus(OutboxStatus.RUNNING)
        .withRerunAfter(rerunAfter)
        .withLastExecution(NOW)
        .build()

    when:
      outboxItem.prepareForProcessing(NOW, rerunAfter)

    then:
      outboxItem == expectedItem
  }

  def "Should not update any fields when 'prepareForProcessing' is called and the item is not eligible for processing"() {
    given:
      def itemBuilder = OutboxItemBuilder.make()
        .withStatus(OutboxStatus.COMPLETED)

      def outboxItem = itemBuilder.build()

    and:
      def rerunAfter = NOW.plusSeconds(1000)
      def expectedItem = itemBuilder.build()

    when:
      outboxItem.prepareForProcessing(NOW, rerunAfter)

    then:
      outboxItem == expectedItem
  }
}
