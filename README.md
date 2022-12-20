# Transactional Outbox Library

[![Build](https://github.com/bluegroundltd/transactional-outbox/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/bluegroundltd/transactional-outbox/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Transactional Outbox is a library that provides a simple way to implement
the [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html) in your
application.

## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
  - [Creating an Outbox library instance](#creating-an-outbox-library-instance)
  - [Creating a new outbox handler](#creating-a-new-outbox-handler)
  - [Creating a new outbox entry](#creating-a-new-outbox-entry)
  - [Monitoring the outbox entries](#monitoring-the-outbox-entries)

## Installation

Transactional Outbox is published on `mavenCentral`. In order to use it just add the following dependency:

```gradle

implementation("io.github.bluegroundltd:transactional-outbox:1.0.0")

```

## Usage

### Creating an Outbox library instance

When you have an operation that you want to be executed asynchronously you can start by configuring the `Outbox`. 
If you're using `Spring` for example, you can configure the library as shown below using the provided builder.

```kotlin
@Configuration
class OutboxConfiguration(
  private val clock: Clock,
  private val outboxHandlers: Set<OutboxHandler>,
  private val postgresLockDao: PostgresLockDao,
  private val outboxStore: OutboxStore,
) {

  @Bean
  fun transactionalOutbox(): TransactionalOutbox {
    val locksProvider = OutboxLocksProvider(postgresLockDao, APPLICATION_SPECIFIC_ID)

    return TransactionalOutboxBuilder
      .make(clock)
      .withHandlers(outboxHandlers)
      .withLocksProvider(locksProvider)
      .withStore(outboxStore)
      .build()
  }
}

private class OutboxLocksProvider(
  private val postgresLockDao: PostgresLockDao,
  private val id: Long
) : OutboxLocksProvider {
  override fun acquire() {
    postgresLockDao.acquire(id)
  }

  override fun release() {
    postgresLockDao.release(id).getOrThrow()
  }
}
```

### Creating a new Outbox Handler

Then you can create a new `OutboxHandler` that will be responsible for processing the `Outbox` entries:

```kotlin
enum class MyOutboxType: OutboxType {
  GOOGLE_CALENDAR_CREATE, OTHER_TYPE;
  
  override fun getType(): String {
    return name
  }
}

class GoogleCalendarCreationOutboxHandler(
  private val userService: UserService,
  private val objectMapper: ObjectMapper
) : OutboxHandler {

  companion object {
    private const val MAX_RETRIES = 5
  }

  override fun getSupportedType() = MyOutboxType.GOOGLE_CALENDAR_CREATE

  override fun serialize(payload: OutboxPayload): String = objectMapper.writeValueAsString(payload)

  override fun getNextExecutionTime(currentRetries: Long): Instant = Instant.now()
    .plusSeconds(NEXT_EXECUTION_TIME_OFFSET_IN_SECONDS[currentRetries.toInt()])

  override fun hasReachedMaxRetries(retries: Long) = retries >= MAX_RETRIES

  override fun handle(payload: String) {
    val deserializedPayload = deserialize(payload)
    userService.createCalendarAndUpdateUser(
      deserializedPayload.userId,
      deserializedPayload.calendarName,
      deserializedPayload.emailsToShareWith
    )
  }

  override fun handleFailure(payload: String) =
    Log.warn("$LOGGER_PREFIX Google calendar creation failed after $MAX_RETRIES retries. Payload: $payload")

  private fun deserialize(payload: String) =
    objectMapper.readValue(payload, GoogleCalendarCreationOutboxPayload::class.java)
}
```

### Creating a new Outbox Entry

Finally, you can create a new `OutboxEntry` and save it to the `Outbox`,
called inside a higher level transaction:

```kotlin
void fun addGoogleCalendarOutboxItem(user: User) {
  val payload = GoogleCalendarCreationOutboxPayload(
    user.getId(),
    user.getFullname(),
    user.getEmail()
  )
  outbox.add(AdminOutboxType.GOOGLE_CALENDAR_CREATE, payload);
}
```

### Monitoring the Outbox Entries

You can monitor the `Outbox` entries by using the `TransactionalOutbox::monitor()` method
using a data store poller. For example, if you're using `Spring` you can use the `@Scheduled` annotation:

```kotlin
@Component
class OutboxMonitor(
  private val transactionalOutbox: TransactionalOutbox
) {

  @Scheduled(fixedDelay = 1000)
  fun monitor() {
    transactionalOutbox.monitor()
  }
}
```
