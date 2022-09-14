package com.blueground.outbox.utils

import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant

trait SpecHelper {
  static SecureRandom srng = new SecureRandom()

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
}
