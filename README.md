# Transactional Outbox Library

[![Build](https://github.com/bluegroundltd/transactional-outbox/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/bluegroundltd/transactional-outbox/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Transactional Outbox is a library that provides a simple way to implement
the [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html) in your
application, developed by Blueground.

API Docs: https://bluegroundltd.github.io/transactional-outbox/

## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
  - [Creating an Outbox library instance](#creating-an-outbox-library-instance)
  - [Creating a new outbox handler](#creating-a-new-outbox-handler)
  - [Creating a new outbox entry](#creating-a-new-outbox-entry)
  - [Monitoring the outbox entries](#monitoring-the-outbox-entries)
  - [Shutting down](#shutting-down)
- [Publishing](#publishing)
- [Maintainers](#maintainers)

## Installation

Transactional Outbox is published on `mavenCentral`. In order to use it just add the following dependency:

```gradle

implementation("io.github.bluegroundltd:transactional-outbox-core:2.1.0")

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
    val monitorLocksProvider = OutboxLocksProvider(postgresLockDao, MONITOR_APPLICATION_SPECIFIC_ID)
    val cleanupLocksProvider = OutboxLocksProvider(postgresLockDao, CLEANUP_APPLICATION_SPECIFIC_ID)

    return TransactionalOutboxBuilder
      .make(clock)
      .withHandlers(outboxHandlers)
      // Ideally you want your thread pool < db connection pool size or similar
      .withThreadPoolSize(5) 
      // Override the default Thread priority with caution
      // If you are facing performance issues, you should
      // adjust the thread pool size first.
      // .withThreadPriority(Thread.NORM_PRIORITY)
      .withMonitorLocksProvider(monitorLocksProvider)
      .withCleanupLocksProvider(cleanupLocksProvider)
      .withStore(outboxStore)
      .build()
  }
}

private class OutboxLocksProviderImpl(
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

Then you can create a new `OutboxHandler` that will be responsible for processing the `Outbox` entries.  
Below you can see a barebones handler, but there's also a utility handler, which uses JSON (de)serialization and
reduces the outbox handlers boilerplate code. Refer to [SimpleOutboxHandler](https://bluegroundltd.github.io/transactional-outbox/core/io.github.bluegroundltd.outbox/-simple-outbox-handler/index.html) in our [docs page](
https://bluegroundltd.github.io/transactional-outbox/index.html).

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
fun addGoogleCalendarOutboxItem(user: User) {
  val payload = GoogleCalendarCreationOutboxPayload(
    user.getId(),
    user.getFullname(),
    user.getEmail()
  )
  outbox.add(MyOutboxType.GOOGLE_CALENDAR_CREATE, payload)
}
```

### Monitoring the Outbox Entries

You can monitor the `Outbox` entries by using the `TransactionalOutbox::monitor` method
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

### Shutting down

You can shut down the `Outbox` by using the `TransactionalOutbox::shutdown` method.

`shutdown()` blocks new tasks from being processed and waits up to a specified period of time for all tasks to be
completed. You can configure this timeout with the `TransactionalOutboxBuilder`.
The default `shutdownTimeout` is set to 5 seconds. If that time expires, the execution is stopped immediately.
Any tasks that did not start execution will have their corresponding item's status set to `PENDING`.

If you're using `Spring` you can use the `@PreDestroy` annotation:

```kotlin
@Component
class OutboxMonitor(
    private val transactionalOutbox: TransactionalOutbox
) {

  @Scheduled(fixedDelay = 1000)
  fun monitor() {
    transactionalOutbox.monitor()
  }

  @PreDestroy
  fun shutdown() {
    transactionalOutbox.shutdown()
  }

}
```
## Local Development
If you're working on a new version of the `core` module, and you want this version to be available to other project before
publishing it, you can do so in two ways

* Alternative 1: Install the new version to Maven
```shell
$ mvn install:install-file \ 
   -Dfile=core/build/libs/core-x.y.z.jar \
   -DgroupId=io.github.bluegroundltd \ 
   -DartifactId=transactional-outbox-core \
   -Dversion=x.y.z \
   -Dpackaging=jar \
   -DgeneratePom=true
```
then depend on the `x.y.z` version as usual. 
```gradle
implementation("io.github.bluegroundltd:transactional-outbox-core:x.y.z")
```
* Alternative 2: Change your dependencies to directly reference the jar file
```gradle
implementation(files("../../../transactional-outbox/core/build/libs/core-x.y.z.jar"))
```
## Publishing

Firstly, bump version in `gradle.properties` of `core` module, commit and push a PR. Once it gets merged, follow one of the two options below.


Now, you can either: 
1. publish via GitHub, or
2. locally from your machine

### Publish via GitHub
Using this method has the benefit of not having to provide any secrets whatsoever.  
Simply, push a git tag **after** a PR is merged, which will trigger the 
[release.yml](.github/workflows/release.yml) pipeline.  
Said pipeline, will publish the artifact.

Please note that this will be automated in future work.

#### Tag formatting
To tag, please follow a format like `v0.4.0`, that is prefixed with `v` and the version number of the artifact you're 
about to release.  

### Publish via your workstation

* Having built the artifact
* Execute the following to upload artifact:
```shell
$ ./gradlew publishAllPublicationsToMavenCentral \
            --no-configuration-cache \
            -Psigning.secretKeyRingFile=<keyring_file_path> \
            -Psigning.password=<keyring_password> \
            -Psigning.keyId=<keyring_id> \
            -PmavenCentralUsername=<nexus_username> \ 
            -PmavenCentralPassword=<nexus_password>
```

Note that if you maintain the properties above (`-P`) in your local `gradle.properties` file, you can omit them from the
command.

## Maintainers

The core maintainer of this project are:

* [Chris Aslanoglou](https://github.com/chris-asl)
* [Apostolos Kiraleos](https://github.com/kiraleos)
* [Thanasis Polydoros](https://github.com/ippokratoys)
* [Grigoris Balaskas](https://github.com/gregBal)
