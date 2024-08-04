package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.annotation.TestableOpenClass
import io.github.bluegroundltd.outbox.item.OutboxItem
import io.github.bluegroundltd.outbox.item.OutboxItemGroup
import io.github.bluegroundltd.outbox.store.OutboxStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock

@TestableOpenClass
internal class OutboxGroupProcessor(
  itemGroup: OutboxItemGroup,
  private val handlerResolver: (item: OutboxItem) -> OutboxHandler?,
  private val store: OutboxStore,
  private val clock: Clock,
) : OutboxProcessingAction {
  companion object {
    private const val LOGGER_PREFIX = "[OUTBOX-GROUP-PROCESSOR]"
    private val logger: Logger = LoggerFactory.getLogger(OutboxGroupProcessor::class.java)
  }

  private val itemProcessors: List<OutboxItemProcessor> = itemGroup.items.map {
    OutboxItemProcessor(it, handlerResolver, store, clock)
  }

  override fun run() {
    logger.info("$LOGGER_PREFIX Processing group with ${itemProcessors.size} items")
    itemProcessors.forEach { it.run() }
  }

  override fun reset() {
    logger.info("$LOGGER_PREFIX Resetting group with ${itemProcessors.size} items")
    itemProcessors.forEach { it.reset() }
  }
}
