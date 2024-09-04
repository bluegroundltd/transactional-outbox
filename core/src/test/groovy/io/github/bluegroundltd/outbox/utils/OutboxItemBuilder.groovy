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
  String groupId

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
      groupId = generateString()

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

  OutboxItemBuilder withLastExecution(Instant lastExecution) {
    this.lastExecution = lastExecution
    this
  }

  OutboxItemBuilder withGroupId(String groupId) {
    this.groupId = groupId
    this
  }

  static OutboxItemBuilder makePending(Instant now = Instant.now()) {
    make().withStatus(OutboxStatus.PENDING).withNextRun(now.minusSeconds(1))
  }

  static OutboxItemBuilder makeRunning(Instant now = Instant.now()) {
    make().withStatus(OutboxStatus.RUNNING).withRerunAfter(now.minusSeconds(1))
  }

  static OutboxItemBuilder makeCompleted() {
    make().withStatus(OutboxStatus.COMPLETED)
  }

  static OutboxItemBuilder makeFailed() {
    make().withStatus(OutboxStatus.FAILED)
  }

  static OutboxItem buildProcessable(Instant now = Instant.now()) {
    makePending(now).buildAndPrepareForProcessing(now)
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

  OutboxItem buildAndPrepareForProcessing(Instant now = Instant.now()) {
    build().with {
      it.prepareForProcessing(now, now.plusSeconds(1000))
      it
    }
  }
}
