package io.github.bluegroundltd.outbox.processing

/**
 * A callback interface for a decorator to be applied to the internal Outbox Item Processors.
 *
 * The primary use case is to set some execution context around the invocation of the outbox processor that is called
 * by the asynchronously-invoked Outbox Item Processor, or to provide some monitoring/statistics for the handler's execution.
 *
 * Implementations must be **thread-safe**
 */
@FunctionalInterface
interface OutboxItemProcessorDecorator {
  fun decorate(runnable: Runnable): Runnable
}
