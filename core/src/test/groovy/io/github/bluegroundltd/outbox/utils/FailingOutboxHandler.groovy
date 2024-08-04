package io.github.bluegroundltd.outbox.utils

import io.github.bluegroundltd.outbox.item.OutboxType
import org.jetbrains.annotations.NotNull

import java.time.Clock

class FailingOutboxHandler extends MockOutboxHandler {
  final static String DEFAULT_FAILURE_REASON = "Failed to handle item"

  private final boolean maxRetriesReached
  private final String failureReason

  FailingOutboxHandler(
    @NotNull OutboxType type,
    Clock clock = null,
    boolean maxRetriesReached = false,
    String failureReason = null
  ) {
    super(type, clock)
    this.maxRetriesReached = maxRetriesReached
    this.failureReason = failureReason ?: DEFAULT_FAILURE_REASON
  }

  @Override
  void handle(String payload) {
    throw new RuntimeException(failureReason)
  }

  @Override
  boolean hasReachedMaxRetries(long retries) {
    return maxRetriesReached
  }
}

