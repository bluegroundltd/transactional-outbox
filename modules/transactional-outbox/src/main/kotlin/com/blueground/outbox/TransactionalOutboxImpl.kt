package com.blueground.outbox

import com.blueground.outbox.item.OutboxItem
import com.blueground.outbox.item.OutboxPayload
import com.blueground.outbox.item.OutboxStatus
import com.blueground.outbox.item.OutboxType

class TransactionalOutboxImpl(
  private val outboxHandlers: List<OutboxHandler>,
  private val outboxPersistor: OutboxPersistor
) : TransactionalOutbox {
  // TODO change handlers storage to MutableMap<OutboxType, OutboxHandler>

  override fun add(type: OutboxType, payload: OutboxPayload) {
    val handler = getHandler(type)
      ?: throw UnsupportedOperationException("Outbox item type \"{${type.getType()}\" isn't supported")

    val outboxItem = makePendingItem(type, payload, handler)
    outboxPersistor.insert(outboxItem)
  }

  // TODO extract to factory
  private fun makePendingItem(type: OutboxType, payload: OutboxPayload, handler: OutboxHandler): OutboxItem {
    return OutboxItem(
      null,
      type,
      OutboxStatus.PENDING,
      handler.serialize(payload),
      0,
      handler.getNextExecutionTime(0),
      null,
      null
    )
  }

  private fun getHandler(type: OutboxType) = outboxHandlers.find { it.supports(type) }

  override fun monitor() = TODO("Not yet implemented")
}
