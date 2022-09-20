package com.blueground.outbox.executor

import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Creates a fixed thread pool executor service, using a default pool size and name format for threads.
 */
class FixedThreadPoolExecutorServiceFactory(
  private val threadPoolSize: Int = DEFAULT_THREAD_POOL_SIZE,
  private val threadNameFormat: String = DEFAULT_THEAD_NAME_FORMAT
) {

  companion object {
    private const val DEFAULT_THREAD_POOL_SIZE = 10
    private const val DEFAULT_THEAD_NAME_FORMAT = "outbox-item-processor-%d"
    private val logger: Logger = LoggerFactory.getLogger(FixedThreadPoolExecutorServiceFactory::class.java)
  }

  fun make(): ExecutorService {
    logger.debug("Creating fixed thread pool with size: $threadPoolSize and name format \"$threadNameFormat\"")
    return Executors.newFixedThreadPool(
      threadPoolSize,
      ThreadFactoryBuilder().setNameFormat(threadNameFormat).build()
    )
  }
}
