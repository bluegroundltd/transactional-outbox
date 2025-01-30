package io.github.bluegroundltd.springoutbox.database

import io.github.bluegroundltd.outbox.item.OutboxStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "outbox_item")
@SequenceGenerator(
  name = "outbox_item_seq",
  sequenceName = "outbox_item_seq",
  allocationSize = 1
)
data class OutboxItemEntity(
  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "outbox_item_seq")
  val id: Long?,

  @Column(name = "type")
  var type: String,

  @Column(name = "status")
  @Enumerated(EnumType.STRING)
  var status: OutboxStatus,

  @Column(name = "payload")
  val payload: String,

  @Column(name = "group_id")
  val groupId: String?,

  @Column(name = "retries")
  var retries: Long,

  @Column(name = "next_run")
  var nextRun: Instant,

  @Column(name = "last_execution")
  var lastExecution: Instant?,

  @Column(name = "rerun_after")
  var rerunAfter: Instant?,

  @Column(name = "delete_after")
  var deleteAfter: Instant?,
) : UpdatableEntity<Long>()
