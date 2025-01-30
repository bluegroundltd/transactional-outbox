package io.github.bluegroundltd.springoutbox

import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component

@Component
internal class SpringOutboxShutdown(
  private val transactionalOutbox: TransactionalOutbox,
) : OutboxShutdown {

  @PreDestroy
  override fun shutdown() {
    transactionalOutbox.shutdown()
  }
}
