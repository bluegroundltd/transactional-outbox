package io.github.bluegroundltd.springoutbox.converter

import io.github.bluegroundltd.outbox.item.OutboxType

/**
 * OutboxTypeConverter is responsible for converting database strings to an OutboxType.
 */
interface OutboxTypeConverter {
  /**
   * Converts a string representation to an OutboxType.
   *
   * @param source the string to convert
   * @return the OutboxType
   */
  fun convert(source: String): OutboxType
}
