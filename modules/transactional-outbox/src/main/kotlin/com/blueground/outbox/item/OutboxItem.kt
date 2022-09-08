package com.blueground.outbox.item

import java.time.Instant

@SuppressWarnings("LongParameterList")
class OutboxItem(
  val id: Long?,
  val type: OutboxType,
  val status: OutboxStatus,
  val payload: String,
  val retries: Int,
  val nextRun: Instant,
  val lastExecution: Instant?,
  val rerunAfter: Instant?, // TODO maybe duration? to be added to lastExecution?
)
