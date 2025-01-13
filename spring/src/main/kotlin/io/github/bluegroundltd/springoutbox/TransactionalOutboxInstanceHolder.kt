package io.github.bluegroundltd.springoutbox

import io.github.bluegroundltd.outbox.TransactionalOutbox

interface TransactionalOutboxInstanceHolder {
  fun getTransactionalOutbox(): TransactionalOutbox
}
