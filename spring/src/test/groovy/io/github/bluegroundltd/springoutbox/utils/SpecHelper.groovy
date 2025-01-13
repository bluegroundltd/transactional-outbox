package io.github.bluegroundltd.springoutbox.utils

import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant

trait SpecHelper {

  static SecureRandom srng = new SecureRandom()

  Integer generateInt(Integer bound = null) {
    (bound != null) ? Math.abs(srng.nextInt(bound)) : Math.abs(srng.nextInt())
  }

  Integer generateIntNonZero(Integer bound = null) {
    generateInt(bound) + 1
  }

  Long generateLong() {
    Math.abs(srng.nextLong())
  }

  String generateString() {
    generateString(10)
  }

  String generateString(int length) {
    String alphaNumeric = "abcdefghijklmnopqrstyvwABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    StringBuilder sb = new StringBuilder(length)

    for (int i = 0; i < length; i++) {
      sb.append(alphaNumeric.charAt(generateInt(alphaNumeric.length())))
    }
    sb.toString()
  }

  Instant generateInstant() {
    long minTime = Timestamp.valueOf("1970-01-01 00:00:00").getTime()
    long maxTime = Timestamp.valueOf("2100-12-31 00:58:00").getTime()

    long diff = maxTime - minTime + 1
    long randomTime = minTime + (long) (srng.nextDouble() * diff)

    Instant.ofEpochMilli(randomTime)
  }

  def <T> T randomEnum(Class<T> enumeration) {
    def list = enumeration.enumConstants as List
    list[generateInt(list.size())]
  }
}
