package io.github.bluegroundltd.outbox

/**
 * A 'runnable' action that will be executed in order to process outbox item(s).
 *
 * Encapsulates both the logic for processing items and the item(s) themselves along with any
 * supporting components (e.g. handler(s), storage, etc.).
 *
 * It is expected that an instance of this interface will be created for each processing action,
 * and it will be run inside an [OutboxProcessingHost].
 */
internal interface OutboxProcessingAction {
  fun run()
  fun reset()
}
