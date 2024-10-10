package io.github.bluegroundltd.outbox.utils

import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxStatus
import io.github.bluegroundltd.outbox.store.OutboxFilter
import io.github.bluegroundltd.outbox.store.OutboxStore
import io.github.bluegroundltd.outbox.store.OutboxStoreInsertHints
import org.jetbrains.annotations.NotNull

import java.time.Instant

class InMemoryOutboxStore implements OutboxStore {
  private final Map<Long, OutboxItem> outboxItems = new HashMap<>()

  @Override
  OutboxItem insert(@NotNull OutboxItem outboxItem) {
    outboxItems[outboxItem.id] = outboxItem
    return outboxItem
  }

  @Override
  OutboxItem insert(@NotNull OutboxItem outboxItem, @NotNull OutboxStoreInsertHints hints) {
    insert(outboxItem)
  }

  @Override
  OutboxItem update(@NotNull OutboxItem outboxItem) {
    if (!outboxItems.containsKey(outboxItem.id)) {
      throw new IllegalArgumentException("Item with id ${outboxItem.id} not found")
    }
    outboxItems[outboxItem.id] = outboxItem
    return outboxItem
  }

  @Override
  List<OutboxItem> fetch(@NotNull OutboxFilter outboxFilter) {
    return outboxItems.values().toList()
  }

  @Override
  void deleteCompletedItems(@NotNull Instant now) {
    outboxItems.removeAll { id, item ->
      item.status == OutboxStatus.COMPLETED && item.deleteAfter.isBefore(now)
    }
  }

  OutboxItem get(@NotNull Long id) {
    return outboxItems[id]
  }

  List<OutboxItem> get(@NotNull List<Long> ids) {
    return ids.unique().collect { get(it) }.findAll { it != null }
  }
}
