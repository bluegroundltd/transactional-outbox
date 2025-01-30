package io.github.bluegroundltd.springoutbox.utils

import io.github.bluegroundltd.outbox.OutboxHandler
import io.github.bluegroundltd.outbox.item.OutboxPayload
import io.github.bluegroundltd.outbox.item.OutboxType
import org.jetbrains.annotations.NotNull

import java.time.Clock
import java.time.Duration
import java.time.Instant

class MockOutboxHandler implements OutboxHandler {
  private final OutboxType type
  private final Clock clock

  MockOutboxHandler(
    @NotNull OutboxType type,
    Clock clock = null
  ) {
    this.type = type
    this.clock = clock ?: Clock.systemUTC()
  }

  @Override
  OutboxType getSupportedType() {
    return type
  }

  @Override
  String serialize(OutboxPayload payload) {
    return "dummyPayload"
  }

  @Override
  Instant getNextExecutionTime(long currentRetries) {
    return Instant.now(clock) + Duration.ofHours(1)
  }

  @Override
  boolean hasReachedMaxRetries(long retries) {
    return false
  }

  @Override
  void handle(String payload) {}

  @Override
  void handleFailure(String payload) {}

  @Override
  Duration getRetentionDuration() {
    return Duration.ofHours(1)
  }
}
