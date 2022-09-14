package com.blueground.outbox.item

import java.time.Instant

@SuppressWarnings("LongParameterList")
class OutboxItem(
  val id: Long?,
  val type: OutboxType,
  var status: OutboxStatus,
  val payload: String,
  var retries: Long,
  var nextRun: Instant,
  var lastExecution: Instant?,
  var rerunAfter: Instant?
)
