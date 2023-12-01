package io.github.bluegroundltd.outbox.unit

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.bluegroundltd.outbox.item.OutboxType
import spock.lang.Shared
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.function.Function

class SimpleOutboxHandlerSpec extends Specification {

  @Shared
  Instant now = Instant.now()
  @Shared
  Integer maxRetries = 10
  OutboxType supportedType = Mock()
  ObjectMapper objectMapper = Mock()
  Function<OutboxPayloadTest, Void> handleWithParsedPayloadCallback
  SimpleOutboxHandlerTestImpl handler

  void setup() {
    Clock clock = Clock.fixed(now, ZoneId.systemDefault())
    handleWithParsedPayloadCallback = Mock()
    handler = new SimpleOutboxHandlerTestImpl(
      supportedType,
      objectMapper,
      clock,
      maxRetries,
      OutboxPayloadTest,
      handleWithParsedPayloadCallback
    )
  }

  def "Should return the given type when `getSupportedType` is invoked"() {
    when:
      def response = handler.getSupportedType()

    then:
      0 * _

    and:
      response == supportedType
  }

  def "Should delegate to object mapper when `serialize` is invoked"() {
    given:
      def payload = new OutboxPayloadTest()
      def serializedValue = GroovyMock(String)

    when:
      def response = handler.serialize(payload)

    then:
      1 * objectMapper.writeValueAsString(payload) >> serializedValue
      0 * _

    and:
      response == serializedValue

  }

  def "Should return #desc when getNextExecutionTime is invoked with #retries retries"() {
    when:
      def response = handler.getNextExecutionTime(retries)

    then:
      0 * _

    and:
      response == expected

    where:
      desc                 | retries | expected
      "now"                | 0       | now
      "1 minute after now" | 1       | now.plusSeconds(60)
      "1 minute after now" | 2       | now.plusSeconds(60)
  }

  def "Should return #expectedResponse when `hasReachedMaxRetries` is invoked with #retries"() {
    when:
      def response = handler.hasReachedMaxRetries(retries)

    then:
      0 * _

    and:
      response == expectedResponse

    where:
      retries        | expectedResponse
      maxRetries - 1 | false
      maxRetries     | true
      maxRetries + 1 | true
  }

  def "Should call deserialize payload by using object mapper and then invoking handler when `handle` is invoked"() {
    given:
      String payload = GroovyMock(String)
      OutboxPayloadTest parsedPayload = GroovyMock(OutboxPayloadTest)

    when:
      handler.handle(payload)

    then:
      1 * objectMapper.readValue(payload, OutboxPayloadTest) >> parsedPayload
      1 * handleWithParsedPayloadCallback.apply(parsedPayload)
      0 * _
  }

  def "Should get the item retention duration when `getRetentionDuration` is invoked"() {
    when:
      def response = handler.getRetentionDuration()

    then:
      0 * _

    and:
      response == Duration.ofDays(10)
  }
}
