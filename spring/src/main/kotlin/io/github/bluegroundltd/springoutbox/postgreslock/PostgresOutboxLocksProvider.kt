package io.github.bluegroundltd.springoutbox.postgreslock

import io.github.bluegroundltd.outbox.OutboxLocksProvider

class PostgresOutboxLocksProvider(
  private val postgresLockDao: PostgresLockDao,
  private val id: Long
) : OutboxLocksProvider {
  override fun acquire() {
    postgresLockDao.acquire(id)
  }

  override fun release() {
    postgresLockDao.release(id).getOrThrow()
  }

  override fun toString(): String {
    return "OutboxLocksProvider(postgresLockDao=$postgresLockDao, id=$id)"
  }
}
