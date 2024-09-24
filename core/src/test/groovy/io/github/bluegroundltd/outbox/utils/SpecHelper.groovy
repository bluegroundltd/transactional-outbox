package io.github.bluegroundltd.outbox.utils

import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant

trait SpecHelper {
  static SecureRandom srng = new SecureRandom()

  // Preferably, these should have been static but there is a bug in Groovy that does not allow static initialization
  // to access other static values: https://issues.apache.org/jira/browse/GROOVY-11267
  private final String alphanumericCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
  private final int alphanumericCount = alphanumericCharacters.length()

  Long generateLong() {
    Math.abs(srng.nextLong())
  }

  Instant generateInstant() {
    long minTime = Timestamp.valueOf("1970-01-01 00:00:00").getTime()
    long maxTime = Timestamp.valueOf("2100-12-31 00:59:00").getTime()

    long diff = maxTime - minTime + 1
    long randomTime = minTime + (long) (Math.random() * diff)

    Instant.ofEpochMilli(randomTime)
  }

  Boolean generateBoolean() {
    srng.nextBoolean()
  }

  int generateInt(Integer bound = null) {
    (bound != null) ? Math.abs(srng.nextInt(bound)) : Math.abs(srng.nextInt())
  }

  int generateIntNonZero(Integer bound = null) {
    generateInt(bound) + 1
  }

  String generateString(int length = 10) {
    StringBuilder sb = new StringBuilder(length)
    for (int i = 0; i < length; i++) {
      sb.append(alphanumericCharacters.charAt(generateInt(alphanumericCount)))
    }
    sb.toString()
  }
}
