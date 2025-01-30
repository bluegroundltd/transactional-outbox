package io.github.bluegroundltd.springoutbox.postgreslock

import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import spock.lang.Specification

class PostgresLockDaoSpec extends Specification {
  def entityManager = Mock(EntityManager)
  def query = Mock(Query)
  def lockDao = new PostgresLockDao(entityManager)

  def "Should create a native query using pg_advisory_lock and the supplied identifier"() {
    given:
      def lockIdentifier = 1L
      def expectedQuery = "select 1 from pg_advisory_lock(:id)"

    when:
      lockDao.acquire(lockIdentifier)

    then:
      1 * entityManager.createNativeQuery(expectedQuery) >> query
      1 * query.setParameter("id", lockIdentifier) >> query
      1 * query.singleResult
      0 * _
  }

  def "Should do nothing if there is a failure, on acquire"() {
    given:
      def lockIdentifier = 1L
      def expectedQuery = "select 1 from pg_advisory_lock(:id)"

    when:
      lockDao.acquire(lockIdentifier)

    then:
      1 * entityManager.createNativeQuery(expectedQuery) >> { throw RuntimeException as Throwable }
      0 * _
  }

  def "Should create a native query using pg_advisory_unlock and the supplied identifier"() {
    given:
      Long lockIdentifier = 1L
      def expectedQuery = "select pg_advisory_unlock(:id)"

    when:
      // Method's signature changed due to Kotlin's name mangling (usage of `Result<...>`).
      lockDao.'release-IoAF18A'(lockIdentifier)

    then:
      1 * entityManager.createNativeQuery(expectedQuery) >> query
      1 * query.setParameter("id", lockIdentifier) >> query
      1 * query.singleResult >> true
      0 * _
  }

  def "Should do nothing if there is a failure, on release"() {
    given:
      Long lockIdentifier = 1L
      def expectedQuery = "select pg_advisory_unlock(:id)"

    when:
      // Method's signature changed due to Kotlin's name mangling (usage of `Result<...>`).
      lockDao.'release-IoAF18A'(lockIdentifier)

    then:
      1 * entityManager.createNativeQuery(expectedQuery) >> query
      1 * query.setParameter("id", lockIdentifier) >> query
      1 * query.singleResult >> true
      0 * _
  }
}
