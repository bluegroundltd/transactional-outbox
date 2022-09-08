package com.blueground.outbox.item

enum class OutboxStatus {
  PENDING,
  PROCESSING,
  COMPLETED,
  FAILED
}
