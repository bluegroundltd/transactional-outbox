package io.github.bluegroundltd.outbox.item

import java.time.Instant

data class OutboxItem(
  val id: Long? = null,
  val type: OutboxType,
  var status: OutboxStatus,
  val payload: String,
  var retries: Long = 0,
  var nextRun: Instant,
  var lastExecution: Instant? = null,
  var rerunAfter: Instant? = null,
  var deleteAfter: Instant? = null,
)
