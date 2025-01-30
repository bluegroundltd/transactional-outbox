package io.github.bluegroundltd.springoutbox.utils

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.springoutbox.TestOutboxType

import java.time.Instant

class OutboxItemBuilder implements SpecHelper {
  private Long id
  private OutboxType type
  private OutboxStatus status
  private String payload
  private Long retries
  private Instant nextRun
  private Instant lastExecution
  private Instant rerunAfter
  private Instant deleteAfter
  private String groupId

  static OutboxItemBuilder make() {
    new OutboxItemBuilder().with {
      id = generateLong()
      type = randomEnum(TestOutboxType)
      status = randomEnum(OutboxStatus)
      payload = generateString()
      retries = generateIntNonZero(5)
      nextRun = generateInstant()
      lastExecution = generateInstant()
      rerunAfter = generateInstant()
      deleteAfter = generateInstant()
      groupId = generateString()
      it
    }
  }

  OutboxItemBuilder withId(Long id) {
    this.id = id
    this
  }

  OutboxItemBuilder withStatus(OutboxStatus status) {
    this.status = status
    this
  }

  OutboxItemBuilder withGroupId(String groupId) {
    this.groupId = groupId
    this
  }

  OutboxItemBuilder withGroupId() {
    withGroupId(generateString())
  }

  OutboxItemBuilder withoutGroupId() {
    withGroupId(null)
  }

  OutboxItemBuilder withRetries(Long retries) {
    this.retries = retries
    this
  }

  OutboxItemBuilder withNextRun(Instant nextRun) {
    this.nextRun = nextRun
    this
  }

  OutboxItemBuilder withLastExecution(Instant lastExecution) {
    this.lastExecution = lastExecution
    this
  }

  OutboxItemBuilder withRerunAfter(Instant rerunAfter) {
    this.rerunAfter = rerunAfter
    this
  }

  OutboxItemBuilder withDeleteAfter(Instant deleteAfter) {
    this.deleteAfter = deleteAfter
    this
  }

  OutboxItem build() {
    new OutboxItem(
      id,
      type,
      status,
      payload,
      retries,
      nextRun,
      lastExecution,
      rerunAfter,
      deleteAfter,
      groupId
    )
  }
}
