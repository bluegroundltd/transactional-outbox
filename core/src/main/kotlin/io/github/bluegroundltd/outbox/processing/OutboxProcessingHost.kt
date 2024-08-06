package io.github.bluegroundltd.outbox.processing

import io.github.bluegroundltd.outbox.annotation.TestableOpenClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Serves as a host inside which outbox items will be processed.
 *
 * @param processingAction the processing action that will be run to process the outbox items
 * @param decorators a list of decorators that will be used to decorate the processor
 *
 * This component allows for separating the responsibilities of processing the items and being scheduled
 * on an executor (i.e. implementing `Runnable`).
 *
 * From a design perspective it would have been preferable to be supplied with a pre-decorated processor.
 * However, this is not currently possible due to the way the decoration process is defined (i.e. on top
 * of [Runnable]). Once this has been changed to work on [OutboxItemProcessor] (see relevant discussion:
 * https://github.com/bluegroundltd/transactional-outbox/discussions/24), it will no longer be the case
 * and this component will be updated accordingly.
 */
@Suppress("TooGenericExceptionCaught")
@TestableOpenClass
internal class OutboxProcessingHost(
  private val processingAction: OutboxProcessingAction,
  decorators: List<OutboxItemProcessorDecorator>
) : Runnable {
  companion object {
    private const val LOGGER_PREFIX = "[OUTBOX-PROCESSING-HOST]"
    private val logger: Logger = LoggerFactory.getLogger(OutboxGroupProcessor::class.java)
  }

  private val processorRunnable = Runnable {
    try {
      processingAction.run()
    } catch (e: Exception) {
      logger.error("$LOGGER_PREFIX ${e.message}")
      processingAction.reset()
    }
  }
  private val finalRunnable: Runnable = decorators
    .fold(processorRunnable) { decorated, decorator -> decorator.decorate(decorated) }

  override fun run() {
    finalRunnable.run()
  }

  fun reset() {
    processingAction.reset()
  }
}
