package com.blueground.outbox

interface TransactionalOutbox {

  fun add()

  fun monitor()
}
