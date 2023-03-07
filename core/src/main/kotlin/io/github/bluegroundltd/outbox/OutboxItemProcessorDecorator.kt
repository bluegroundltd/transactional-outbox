package io.github.bluegroundltd.outbox

/**
 * A callback interface for a decorator to be applied to the internal Outbox Item Processor.
 *
 * The primary use case is to set some execution context around the invocation of the {@link OutboxHandler} that is called
 * by the asynchronously-invoked Outbox Item Processor, or to provide some monitoring/statistics for the handler's execution.
 */
@FunctionalInterface
interface OutboxItemProcessorDecorator {
  fun decorate(runnable: Runnable): Runnable
}
