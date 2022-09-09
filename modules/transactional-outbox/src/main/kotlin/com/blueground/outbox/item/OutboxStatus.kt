package com.blueground.outbox.item

enum class OutboxStatus {
  PENDING,
  RUNNING,
  COMPLETED,
  FAILED
}
