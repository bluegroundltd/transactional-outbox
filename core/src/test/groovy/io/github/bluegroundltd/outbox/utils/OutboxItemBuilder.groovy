package io.github.bluegroundltd.outbox.utils

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.item.OutboxType

import java.time.Instant

class OutboxItemBuilder implements SpecHelper {
  Long id
  OutboxType type
  OutboxStatus status
  String payload
  Long retries
  Instant nextRun
  Instant lastExecution
  Instant rerunAfter
  Instant deleteAfter

  static OutboxItemBuilder make() {
    new OutboxItemBuilder().with {
      id = generateLong()
      type = new DummyOutboxType()
      status = OutboxStatus.PENDING
      payload = "dummyPayload"
      retries = generateInt(10)
      nextRun = generateInstant()
      lastExecution = null
      rerunAfter = null
      deleteAfter = null

      it
    }
  }

  OutboxItemBuilder withType(OutboxType type) {
    this.type = type
    this
  }

  OutboxItemBuilder withStatus(OutboxStatus status) {
    this.status = status
    this
  }

  OutboxItemBuilder withPayload(String payload) {
    this.payload = payload
    this
  }

  OutboxItemBuilder withNextRun(Instant nextRun) {
    this.nextRun = nextRun
    this
  }

  OutboxItemBuilder withRerunAfter(Instant rerunAfter) {
    this.rerunAfter = rerunAfter
    this
  }

  OutboxItemBuilder withRerunAfter() {
    withRerunAfter(generateInstant())
  }

  OutboxItemBuilder withoutRerunAfter() {
    withRerunAfter(null)
  }

  static OutboxItem makePending() {
    make().build()
  }

  static OutboxItem makeRunning() {
    make().withStatus(OutboxStatus.RUNNING).build()
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
      deleteAfter
    )
  }
}

class DummyOutboxType implements OutboxType {
  @Override
  String getType() {
    return "dummyOutboxType"
  }
}
