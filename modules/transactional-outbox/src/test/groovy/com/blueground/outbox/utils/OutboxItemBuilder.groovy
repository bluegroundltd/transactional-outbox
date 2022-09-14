package com.blueground.outbox.utils

import com.blueground.outbox.item.OutboxItem
import com.blueground.outbox.item.OutboxStatus
import com.blueground.outbox.item.OutboxType

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

  static OutboxItemBuilder make() {
    new OutboxItemBuilder().with {
      id = generateLong()
      type = new DummyOutboxType()
      status = OutboxStatus.PENDING
      payload = "dummyPayload"
      retries = 0
      nextRun = generateInstant()
      lastExecution = null
      rerunAfter = null

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

  static OutboxItem makePending() {
    make().build()
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
      rerunAfter
    )
  }
}

class DummyOutboxType implements OutboxType {
  @Override
  String getType() {
    return "dummyOutboxType"
  }
}
