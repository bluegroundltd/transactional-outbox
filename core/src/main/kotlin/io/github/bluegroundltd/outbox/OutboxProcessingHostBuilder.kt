package io.github.bluegroundltd.outbox

import io.github.bluegroundltd.outbox.annotation.TestableOpenClass

/**
 * Builds an [OutboxProcessingHost] instance.
 *
 * The main purpose of this component is to enhance/simplify testing since it allows for mocking the instances
 * that are submitted to the executor. Accordingly, a lot of the unit tests have been adapted to make use of this
 * functionality.
 */
@TestableOpenClass
internal class OutboxProcessingHostBuilder {
  fun build(
    processingAction: OutboxProcessingAction,
    decorators: List<OutboxItemProcessorDecorator>
  ) = OutboxProcessingHost(processingAction, decorators)
}
