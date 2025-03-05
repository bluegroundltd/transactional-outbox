package io.github.bluegroundltd.springoutbox

import jakarta.annotation.PreDestroy

open class SpringOutboxShutdown(
  private val transactionalOutbox: TransactionalOutbox,
) : OutboxShutdown {

  @PreDestroy
  override fun shutdown() {
    transactionalOutbox.shutdown()
  }
}
