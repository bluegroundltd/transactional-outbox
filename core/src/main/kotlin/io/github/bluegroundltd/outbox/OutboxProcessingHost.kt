package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.annotation.TestableOpenClass

/**
 * Serves as a host inside which outbox items will be processed.
 *
 * @param processor the processor that will be used to process the outbox items
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
@TestableOpenClass
internal class OutboxProcessingHost(
  private val processor: OutboxItemProcessor,
  decorators: List<OutboxItemProcessorDecorator>
) : Runnable {
  private val runnable: Runnable = decorators
    .fold(processor as Runnable) { decorated, decorator -> decorator.decorate(decorated) }

  override fun run() {
    runnable.run()
  }

  fun reset() {
    processor.reset()
  }
}
