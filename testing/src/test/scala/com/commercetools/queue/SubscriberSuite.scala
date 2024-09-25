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

import cats.effect.IO
import cats.effect.std.AtomicCell
import cats.effect.testkit.TestControl
import cats.implicits.catsSyntaxOptionId
import cats.syntax.either._
import cats.syntax.traverse._
import com.commercetools.queue.testing._
import munit.CatsEffectSuite

import scala.concurrent.duration._

class SubscriberSuite extends CatsEffectSuite {

  val queueSub = ResourceFunFixture(
    TestQueue[String](name = "test-queue", messageTTL = 15.minutes, lockTTL = 1.minute).toResource
      .map { queue =>
        (queue, TestQueueSubscriber(queue), TestQueuePublisher(queue))
      })

  def produceMessages(queue: TestQueue[String], count: Int): IO[List[TestMessage[String]]] =
    List
      .range(0, count)
      .traverse { i =>
        IO.sleep(10.millis) *> IO.realTimeInstant.map(TestMessage(i.toString, _))
      }
      .flatTap(queue.setAvailableMessages)

  queueSub.test("Successful message batch must be acked/nacked") { case (queue, subscriber, _) =>
    TestControl
      .executeEmbed(for {
        // first populate the queue
        _ <- produceMessages(queue, 5)
        // ack first batch, nack second
        _ <- subscriber
          .messageBatches(batchSize = 3, waitingTime = 40.millis)
          .zipWithIndex
          .evalTap { case (batch, index) =>
            index match {
              case 1 => batch.nackAll
              case _ => batch.ackAll
            }
          }
          .take(2)
          .compile
          .drain
        _ <- assertIO(queue.getAvailableMessages.map(_.map(_.payload)), List("3", "4"))
        _ <- assertIO(queue.getLockedMessages, Nil)
        _ <- assertIO(queue.getDelayedMessages, Nil)
      } yield ())
  }

  queueSub.test("Successful messages must be acked") { case (queue, subscriber, _) =>
    TestControl
      .executeEmbed(for {
        // first populate the queue
        _ <- produceMessages(queue, 100)
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
    case (queue, subscriber, _) =>
      TestControl
        .executeEmbed(for {
          // first populate the queue
          messages <- produceMessages(queue, 100)
          result <- subscriber
            // take all messages in one big batch
            .processWithAutoAck(batchSize = 100, waitingTime = 40.millis)(m =>
              IO.raiseWhen(m.rawPayload == "43")(new Exception("BOOM")).as(m))
            .attempt
            .compile
            .toList
        } yield (messages, result))
        .flatMap { case (originals, result) =>
          for {
            // check that all messages were consumed up to message #43
            _ <- assertIO(IO.pure(result.init.map(_.map(_.rawPayload))), originals.take(43).map(m => Right(m.payload)))
            _ <- assertIO(IO.pure(result.last.leftMap(_.getMessage())), Left("BOOM"))
            _ <- assertIO(queue.getAvailableMessages, originals.drop(43))
            _ <- assertIO(queue.getLockedMessages, Nil)
            _ <- assertIO(queue.getDelayedMessages, Nil)
          } yield ()
        }
  }

  queueSub.test("Messages consumed and ok'ed or drop'ed should follow the decision") {
    case (queue, subscriber, publisher) =>
      TestControl
        .executeEmbed(for {
          // first populate the queue
          _ <- produceMessages(queue, 100)
          result <- subscriber
            .process[Int](batchSize = 5, waitingTime = 40.millis, publisher)((msg: Message[IO, String]) =>
              if (msg.rawPayload.toInt % 2 == 0) IO.pure(Decision.Drop)
              else IO.pure(Decision.Ok(1)))
            .interruptAfter(3.seconds)
            .compile
            .foldMonoid
        } yield result)
        .flatMap { result =>
          for {
            _ <- assertIO(queue.getAvailableMessages, Nil)
            _ = assertEquals(result, 50.asRight)
          } yield ()
        }
  }

  queueSub.test("Messages consumed and confirmed or dropped should follow the decision") {
    case (queue, subscriber, publisher) =>
      TestControl
        .executeEmbed(for {
          // first populate the queue
          _ <- produceMessages(queue, 100)
          result <- subscriber
            .process[Int](batchSize = 5, waitingTime = 40.millis, publisher)((msg: Message[IO, String]) =>
              if (msg.rawPayload.toInt % 2 == 0) IO.pure(Decision.Ok(1))
              else IO.pure(Decision.Drop))
            .take(50)
            .compile
            .foldMonoid
        } yield result)
        .flatMap { result =>
          for {
            _ <- assertIO(queue.getAvailableMessages, Nil)
            _ = assertEquals(result, 50.asRight)
          } yield ()
        }
  }

  queueSub.test("Messages consumed and requeued should follow the decision") { case (queue, subscriber, publisher) =>
    TestControl
      .executeEmbed(for {
        _ <- produceMessages(queue, 100)
        opCounter <- AtomicCell[IO].of(0)
        result <- subscriber
          .process[Int](batchSize = 5, waitingTime = 40.millis, publisher)((msg: Message[IO, String]) =>
            opCounter.update(_ + 1) >> {
              // let's reenqueue at the first run, and then confirm
              if (msg.metadata.contains("reenqueued")) IO.pure(Decision.Ok(1))
              else IO.pure(Decision.Reenqueue(Map("reenqueued" -> "true").some, None))
            })
          .take(100)
          .compile
          .foldMonoid
        totOpCount <- opCounter.get
      } yield (result, totOpCount))
      .flatMap { case (result, totOpCount) =>
        for {
          _ <- assertIO(queue.getAvailableMessages, Nil)
          _ = assertEquals(totOpCount, 200)
          _ = assertEquals(result, 100.asRight)
        } yield ()
      }
  }

  queueSub.test("Messages that are marked as failed and acked should follow the decision") {
    case (queue, subscriber, publisher) =>
      TestControl
        .executeEmbed(for {
          // first populate the queue
          _ <- produceMessages(queue, 100)
          result <- subscriber
            .process[Int](batchSize = 5, waitingTime = 40.millis, publisher)((msg: Message[IO, String]) =>
              IO.pure(Decision.Fail(new Throwable(s"failed ${msg.rawPayload}"), ack = true)))
            .take(100)
            .collect { case Left(_) => 1 }
            .compile
            .foldMonoid
        } yield result)
        .flatMap { result =>
          for {
            _ <- assertIO(queue.getAvailableMessages, Nil)
            _ = assertEquals(result, 100)
          } yield ()
        }
  }

  queueSub.test("Messages that are marked as failed and not acked should follow the decision") {
    case (queue, subscriber, publisher) =>
      TestControl
        .executeEmbed(for {
          // first populate the queue
          _ <- produceMessages(queue, 100)
          result <- subscriber
            .process[Int](batchSize = 5, waitingTime = 40.millis, publisher)((msg: Message[IO, String]) =>
              IO.pure(Decision.Fail(new Throwable(s"failed ${msg.rawPayload}"), ack = false)))
            .take(100)
            .collect { case Left(_) => 1 }
            .compile
            .foldMonoid
        } yield result)
        .flatMap { result =>
          for {
            _ <- assertIO(queue.getAvailableMessages.map(_.size), 100)
            _ = assertEquals(result, 100)
          } yield ()
        }
  }
}
