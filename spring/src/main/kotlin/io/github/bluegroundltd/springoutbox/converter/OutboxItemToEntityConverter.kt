package io.github.bluegroundltd.springoutbox.converter

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.springoutbox.database.OutboxItemEntity
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

@Component
internal class OutboxItemToEntityConverter : Converter<OutboxItem, OutboxItemEntity> {
  override fun convert(source: OutboxItem): OutboxItemEntity {
    return OutboxItemEntity(
      id = source.id,
      type = source.type.getType(),
      status = source.status,
      payload = source.payload,
      groupId = source.groupId,
      retries = source.retries,
      nextRun = source.nextRun,
      lastExecution = source.lastExecution,
      rerunAfter = source.rerunAfter,
      deleteAfter = source.deleteAfter,
    )
  }
}
