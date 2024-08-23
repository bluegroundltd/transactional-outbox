package io.github.bluegroundltd.outbox.unit.executor

import io.github.bluegroundltd.outbox.executor.FixedThreadPoolExecutorServiceFactory
import io.github.bluegroundltd.outbox.utils.UnitTestSpecification
import java.util.concurrent.ThreadPoolExecutor

class FixedThreadPoolExecutorServiceFactorySpec extends UnitTestSpecification {

  def "Should create a FixedThreadPool ExecutorService with default configuration"() {
    when:
      def factory = new FixedThreadPoolExecutorServiceFactory()

    then:
      def executorService = factory.make()

    and:
      executorService instanceof ThreadPoolExecutor

    then:
      def threadPoolExecutorService = (ThreadPoolExecutor) executorService

    and:
      threadPoolExecutorService.getThreadFactory()

  }

  def "Should create a FixedThreadPool ExecutorService with custom configuration"() {
    given:
      def expectedThreadPoolSize = 5
      def expectedThreadPriority = Thread.MIN_PRIORITY

    when:
      def factory = new FixedThreadPoolExecutorServiceFactory(5, Thread.MIN_PRIORITY, "")

    then:
      def executorService = factory.make()

    and:
      executorService instanceof ThreadPoolExecutor

    then:
      def threadPoolExecutorService = (ThreadPoolExecutor) executorService

    and:
      threadPoolExecutorService.getCorePoolSize() == expectedThreadPoolSize
      threadPoolExecutorService.getMaximumPoolSize() == expectedThreadPoolSize
      threadPoolExecutorService.getThreadFactory().newThread({}).getPriority() == expectedThreadPriority
  }
}
