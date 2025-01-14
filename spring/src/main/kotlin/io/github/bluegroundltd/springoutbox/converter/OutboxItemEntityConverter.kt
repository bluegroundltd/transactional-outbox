package io.github.bluegroundltd.springoutbox.converter

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.springoutbox.database.OutboxItemEntity
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

@Component
internal class OutboxItemEntityConverter(
  private val outboxTypeConverter: OutboxTypeConverter,
) : Converter<OutboxItemEntity, OutboxItem> {
  override fun convert(source: OutboxItemEntity): OutboxItem {
    return OutboxItem(
      id = source.id,
      type = outboxTypeConverter.convert(source.type),
      status = source.status,
      payload = source.payload,
      groupId = source.groupId,
      retries = source.retries,
      nextRun = source.nextRun,
      lastExecution = source.lastExecution,
      rerunAfter = source.rerunAfter,
      deleteAfter = source.deleteAfter
    )
  }
}
