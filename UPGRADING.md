# Upgrade Guide

## v.2.x.x

### v.2.0.0 - Completed outbox items cleanup coordination

Release 2.0.0 introduces coordination in the cleanup process across instances using locks.

#### `TransactionalOutboxBuilder#withLocksProvider` has been removed in favor of `withMonitorLocksProvider`   
The reason for this breaking change was the need to introduce a different locks provider for the completed outbox items cleanup process.  
We could reuse the same locks provider for both the monitor and cleanup process, but this would entail serialized execution of the two processes, which was not desirable.    
The requirement for the new locks provider is solely to be independent of the locks provider used in the monitor process.  
For example, if the locks provider implementation is using Postgres advisory locks, the monitor and the cleanup locks should use a different lock identifier.

**Required changes**  
The `TransactionalOutboxBuilder` call needs to be updated from 
```kotlin
TransactionalOutboxBuilder
  .make(clock)
  .withHandlers(outboxHandlers)
  .withLocksProvider(locksProvider)
  .withStore(outboxStore)
  .build()
```
to
```kotlin
TransactionalOutboxBuilder
  .make(clock)
  .withHandlers(outboxHandlers)
  .withMonitorLocksProvider(PostgresOutboxLocksProvider(LOCKS_MONITOR_ID))
  .withCleanupLocksProvider(PostgresOutboxLocksProvider(LOCKS_CLEANUP_ID))
  .withStore(outboxStore)
  .build()
```
N.B.: The above assumes that the locks provider implementation is using Postgres advisory locks.

## v.1.x.x 

### v.1.0.0 - Completed outbox items cleanup

Release 1.0 introduces a cleanup process for the outbox items that have been successfully processed, thus reducing the size of the outbox table, which can grow quite large.  
When the outbox items are processes successfully, in addition to be marked as completed, their `OutboxItem.deleteAfter` field is set to `now() + retentionPeriod`.  
The cleanup process, like monitor, should be run periodically, depending on your needs. Once run, it deletes the completed
outbox items whose `deleteAfter` is earlier than the current time.

It is advisable to manually delete the already completed outbox items before upgrading to 1.0.0, as the cleanup process
will issue a deletion, which may be quite heavy in terms of I/O operations, hence timeouts may occur on the first run.

**Required changes**  
In the `OutboxStore` implementing class, the `deleteCompletedItems(now: Instant)` method needs to be implemented.
The method should simply delete the outbox items with status `COMPLETED` with a `deleteAfter` earlier than the provided `now` parameter.

Finally, the retention duration period can be defined per outbox handler for flexibility.  
A new `OutboxHandler` method has been added `getRetentionDuration(): Duration` which should return the retention period for the outbox items of the handler.  
Feel free to look the [SimpleOutboxHandler](./core/src/main/kotlin/io/github/bluegroundltd/outbox/SimpleOutboxHandler.kt) for an example.
