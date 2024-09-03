package io.github.bluegroundltd.outbox.item

import java.time.Instant

/**
 * Represents an outbox item.
 *
 * @param groupId an arbitrary value that can be used to group outbox items together. The value is defined as nullable
 * to allow for backward compatibility with existing outbox items. However, for all intents and purposes, the library
 * expects this value to be non-null. At a later stage, the value will be made non-nullable which would introduce a
 * breaking change.
 */
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
  val groupId: String? = null
)
