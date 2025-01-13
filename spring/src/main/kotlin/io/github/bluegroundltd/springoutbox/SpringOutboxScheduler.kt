package io.github.bluegroundltd.springoutbox

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
internal class SpringOutboxScheduler(
  private val transactionalOutbox: TransactionalOutbox,
) : OutboxScheduler {
  companion object {
    private const val LOGGER_PREFIX = "[OUTBOX-SCHEDULED-JOB]"
    private val logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(cron = "\${jobs.transactionalOutbox.scheduler.cron:0 * * * * ?}")
  @Transactional
  @Suppress("TooGenericExceptionCaught")
  override fun run() {
    try {
      logger.info("$LOGGER_PREFIX Initializing outbox monitor")
      transactionalOutbox.monitor()
      logger.info("$LOGGER_PREFIX Outbox monitor initialized")
    } catch (exception: Exception) {
      logger.error("$LOGGER_PREFIX Outbox monitor invocation failed", exception)
    }
  }
}
