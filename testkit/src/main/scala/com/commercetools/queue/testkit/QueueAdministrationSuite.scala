package com.commercetools.queue.testkit

import com.commercetools.queue.QueueConfiguration
import munit.CatsEffectSuite

import scala.concurrent.duration._

/**
 * This suite tests that the features of a [[com.commercetools.queue.QueueAdministration QueueAdministration]] are properly
 * implemented for a concrete client.
 */
trait QueueAdministrationSuite extends CatsEffectSuite { self: QueueClientSuite =>

  withQueue.test("existing queue should be indicated as such") { queueName =>
    val client = clientFixture()
    assertIO(client.administration.exists(queueName), true)
  }

  test("non existing queue should be indicated as such") {
    val client = clientFixture()
    assertIO(client.administration.exists("not-existing"), false)
  }

  withQueue.test("get configuration") { queueName =>
    val admin = clientFixture().administration
    assertIO(admin.configuration(queueName), QueueConfiguration(originalMessageTTL, originalLockTTL))
  }

  withQueue.test("configuration can be updated") { queueName =>
    assume(queueUpdateSupported, "The test environment does not support queue update")
    val admin = clientFixture().administration
    for {
      _ <- admin.update(queueName, Some(originalMessageTTL + 1.minute), Some(originalLockTTL + 10.seconds))
      _ <- assertIO(
        admin.configuration(queueName),
        QueueConfiguration(originalMessageTTL + 1.minute, originalLockTTL + 10.seconds))
    } yield ()
  }

}
