/*
 * Copyright 2024 Commercetools GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.commercetools.queue

import cats.collections.Heap
import cats.effect.IO
import cats.effect.std.AtomicCell
import cats.effect.testkit.TestControl
import cats.syntax.either._
import cats.syntax.traverse._
import com.commercetools.queue.testing._
import munit.CatsEffectSuite

import scala.concurrent.duration._

class SubscriberSuite extends CatsEffectSuite {

  val queueSub = ResourceFixture(
    AtomicCell[IO]
      .of(testing.QueueState[String](Heap.empty, List.empty, Map.empty))
      .map { state =>
        val queue =
          new TestQueue[String](name = "test-queue", state = state, messageTTL = 15.minutes, lockTTL = 1.minute)
        (queue, new TestQueueSubscriber(queue))
      }
      .toResource)

  queueSub.test("Successful messages must be acked") { case (queue, subscriber) =>
    TestControl
      .executeEmbed(for {
        // first populate the queue
        messages <- List.range(0, 100).traverse { i =>
          IO.sleep(10.millis) *> IO.realTimeInstant.map(TestMessage(s"message-$i", _))
        }
        _ <- queue.setAvailableMessages(messages)
        // then process messages in batches of 5
        // processing is (virtually) instantaneous in this case,
        // so messages are immediately acked, from the mocked time PoV
        // however, receiving messages waits for the amount of provided `waitingTime`
        // in the test queue implementation, event if enough messages are available
        // so this step makes time progress in steps of `waitingTime`
        result <- subscriber
          .processWithAutoAck(batchSize = 5, waitingTime = 40.millis)(_ => IO.pure(1))
          .interruptAfter(3.seconds)
          .compile
          .foldMonoid
      } yield result)
      .flatMap { result =>
        for {
          _ <- assertIO(IO.pure(result), 100)
          _ <- assertIO(queue.getAvailableMessages, Nil)
          _ <- assertIO(queue.getLockedMessages, Nil)
          _ <- assertIO(queue.getDelayedMessages, Nil)
        } yield ()
      }
  }

  queueSub.test("Messages must be unack'ed if processing fails and emit everything up to failure") {
    case (queue, subscriber) =>
      TestControl
        .executeEmbed(for {
          // first populate the queue
          messages <- List.range(0, 100).traverse { i =>
            IO.sleep(10.millis) *> IO.realTimeInstant.map(TestMessage(s"message-$i", _))
          }
          _ <- queue.setAvailableMessages(messages)
          result <- subscriber
            // take all messages in one big batch
            .processWithAutoAck(batchSize = 100, waitingTime = 40.millis)(m =>
              IO.raiseWhen(m.payload == "message-43")(new Exception("BOOM")).as(m))
            .attempt
            .compile
            .toList
        } yield (messages, result))
        .flatMap { case (originals, result) =>
          for {
            // check that all messages were consumed up to message #43
            _ <- assertIO(IO.pure(result.init.map(_.map(_.payload))), originals.take(43).map(m => Right(m.payload)))
            _ <- assertIO(IO.pure(result.last.leftMap(_.getMessage())), Left("BOOM"))
            _ <- assertIO(queue.getAvailableMessages, originals.drop(43))
            _ <- assertIO(queue.getLockedMessages, Nil)
            _ <- assertIO(queue.getDelayedMessages, Nil)
          } yield ()
        }
  }

}
