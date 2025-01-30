package io.github.bluegroundltd.springoutbox.utils

import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType
import io.github.bluegroundltd.springoutbox.TestOutboxType
import io.github.bluegroundltd.springoutbox.database.OutboxItemEntity

import java.time.Instant

class OutboxItemEntityBuilder implements SpecHelper {
  private Long id
  private String type
  private OutboxStatus status
  private String payload
  private String groupId
  private Long retries
  private Instant nextRun
  private Instant lastExecution
  private Instant rerunAfter
  private Instant deleteAfter

  static OutboxItemEntityBuilder make() {
    new OutboxItemEntityBuilder().with {
      id = generateLong()
      type = randomEnum(TestOutboxType).type
      status = randomEnum(OutboxStatus)
      payload = generateString()
      groupId = generateString()
      retries = generateIntNonZero(5)
      nextRun = generateInstant()
      lastExecution = generateInstant()
      rerunAfter = generateInstant()
      deleteAfter = generateInstant()
      it
    }
  }

  OutboxItemEntityBuilder withId(Long id) {
    this.id = id
    this
  }

  OutboxItemEntityBuilder withType(OutboxType type) {
    this.type = type.type
    this
  }

  OutboxItemEntityBuilder withStatus(OutboxStatus status) {
    this.status = status
    this
  }

  OutboxItemEntityBuilder withGroupId(String groupId) {
    this.groupId = groupId
    this
  }

  OutboxItemEntityBuilder withGroupId() {
    withGroupId(generateString())
  }

  OutboxItemEntityBuilder withoutGroupId() {
    withGroupId(null)
  }

  OutboxItemEntityBuilder withRetries(Long retries) {
    this.retries = retries
    this
  }

  OutboxItemEntityBuilder withNextRun(Instant nextRun) {
    this.nextRun = nextRun
    this
  }

  OutboxItemEntityBuilder withLastExecution(Instant lastExecution) {
    this.lastExecution = lastExecution
    this
  }

  OutboxItemEntityBuilder withRerunAfter(Instant rerunAfter) {
    this.rerunAfter = rerunAfter
    this
  }

  OutboxItemEntityBuilder withDeleteAfter(Instant deleteAfter) {
    this.deleteAfter = deleteAfter
    this
  }

  OutboxItemEntity build() {
    new OutboxItemEntity(
      id,
      type,
      status,
      payload,
      groupId,
      retries,
      nextRun,
      lastExecution,
      rerunAfter,
      deleteAfter
    )
  }
}
