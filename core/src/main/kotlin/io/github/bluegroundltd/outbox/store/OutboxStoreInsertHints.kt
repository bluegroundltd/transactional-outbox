package io.github.bluegroundltd.outbox.store

data class OutboxStoreInsertHints(
  val forInstantProcessing: Boolean,
  val instantOrderingEnabled: Boolean
)
