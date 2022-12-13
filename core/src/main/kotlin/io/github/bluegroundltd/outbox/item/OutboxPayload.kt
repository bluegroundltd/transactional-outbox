package io.github.bluegroundltd.outbox.item

/**
 * OutboxPayload is the payload of an outbox item, modeled as a marker interface, for clients to use for their payload classes.
 *
 * Example:
 * ```
 * data class MyOutboxPayload(
 *  val id: String,
 *  val name: String
 * ) : OutboxPayload
 * ```
 */
interface OutboxPayload
