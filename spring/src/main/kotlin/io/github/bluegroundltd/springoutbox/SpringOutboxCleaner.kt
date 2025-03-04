package io.github.bluegroundltd.springoutbox

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional

open class SpringOutboxCleaner(
  private val transactionalOutbox: TransactionalOutbox,
) : OutboxCleaner {
  companion object {
    private const val LOGGER_PREFIX = "[OUTBOX-CLEANUP-JOB]"
    private val logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(cron = "\${jobs.transactionalOutbox.cleaner.cron:0 * * * * ?}")
  @Transactional
  @Suppress("TooGenericExceptionCaught")
  override fun run() {
    try {
      logger.info("$LOGGER_PREFIX Initializing outbox cleanup")
      transactionalOutbox.cleanup()
      logger.info("$LOGGER_PREFIX Outbox cleanup initialized")
    } catch (exception: Exception) {
      logger.error("$LOGGER_PREFIX Outbox cleanup invocation failed", exception)
    }
  }
}
