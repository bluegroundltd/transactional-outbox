package io.github.bluegroundltd.springoutbox.postgreslock

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Provides distributed locking functionality via Postgres session-level advisory locks.
 *
 * @see <a href=https://stackoverflow.com/questions/31335211/autowired-vs-persistencecontext-for-entitymanager-bean>
 *   PersistenceContext vs autowired entity manager</a>
 */
@Component
class PostgresLockDao(
  @PersistenceContext private val entityManager: EntityManager
) {

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(PostgresLockDao::class.java)
    private const val LOGGER_PREFIX = "[POSTGRES-LOCKS-PROVIDER]"
    private const val PARAMETER_LOCK_IDENTIFIER = "id"
  }

  /**
   * Acquires an exclusive session-level advisory lock, waiting if needed.
   *
   * Uses "select 1 from ...", because pg_advisory_lock returns void and singleResult crashes upon
   * deserialization with "No Dialect mapping for JDBC type: 1111". Related SO Q&A:
   * https://stackoverflow.com/questions/12557957/jpa-hibernate-call-postgres-function-void-return-mappingexception
   *
   * @see <a href="https://www.postgresql.org/docs/current/explicit-locking.html#ADVISORY-LOCKS">Advisory locks</a>
   * @see <a href="https://www.postgresql.org/docs/current/functions-admin.html#FUNCTIONS-ADVISORY-LOCKS">Advisory locks - available functions</a>
   */
  @Transactional(propagation = Propagation.MANDATORY)
  fun acquire(id: Long) {
    runCatching {
      logger.debug("$LOGGER_PREFIX Acquiring PG advisory lock with id: $id")
      entityManager
        .createNativeQuery("select 1 from pg_advisory_lock(:id)")
        .setParameter(PARAMETER_LOCK_IDENTIFIER, id)
        .singleResult
    }.onFailure {
      logger.error("$LOGGER_PREFIX Failed to acquire PG advisory lock with id: $id", it)
    }
  }

  /**
   * Releases an exclusive session-level advisory lock, waiting if needed.
   *
   * @throws IllegalStateException when the lock is not successfully released
   *
   * @see <a href="https://www.postgresql.org/docs/current/explicit-locking.html#ADVISORY-LOCKS">Advisory locks</a>
   * @see <a href="https://www.postgresql.org/docs/current/functions-admin.html#FUNCTIONS-ADVISORY-LOCKS">Advisory locks - available functions</a>
   */
  @Throws(IllegalStateException::class)
  @Transactional(propagation = Propagation.MANDATORY)
  fun release(id: Long): Result<Unit> {
    logger.debug("$LOGGER_PREFIX Releasing PG advisory lock with id: $id")
    val releaseSucceeded = entityManager
      .createNativeQuery("select pg_advisory_unlock(:id)")
      .setParameter(PARAMETER_LOCK_IDENTIFIER, id)
      .singleResult as Boolean

    return if (releaseSucceeded) {
      Result.success(Unit)
    } else {
      return Result.failure(IllegalStateException("Failed to release PG advisory lock with id: $id"))
    }
  }
}
