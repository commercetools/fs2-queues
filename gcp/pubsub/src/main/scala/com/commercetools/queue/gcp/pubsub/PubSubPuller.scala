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

package com.commercetools.queue.gcp.pubsub

import cats.effect.Async
import cats.effect.syntax.concurrent._
import cats.syntax.all._
import com.commercetools.queue.{Deserializer, MessageContext, QueuePuller}
import com.google.api.gax.httpjson.HttpJsonCallContext
import com.google.api.gax.retrying.RetrySettings
import com.google.api.gax.rpc.DeadlineExceededException
import com.google.cloud.pubsub.v1.stub.SubscriberStub
import com.google.pubsub.v1.{ModifyAckDeadlineRequest, PullRequest, ReceivedMessage, SubscriptionName}
import fs2.Chunk
import org.threeten.bp.Duration

import java.time
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class PubSubPuller[F[_], T](
  val queueName: String,
  subscriptionName: SubscriptionName,
  subscriber: SubscriberStub,
  lockTTLSeconds: Int
)(implicit
  F: Async[F],
  deserializer: Deserializer[T])
  extends QueuePuller[F, T] {

  override def pullBatch(batchSize: Int, waitingTime: FiniteDuration): F[Chunk[MessageContext[F, T]]] =
    wrapFuture(F.delay {
      subscriber
        .pullCallable()
        .withDefaultCallContext(
          HttpJsonCallContext
            .createDefault()
            .withRetrySettings(
              RetrySettings.newBuilder().setLogicalTimeout(Duration.ofMillis(waitingTime.toMillis)).build()))
        .futureCall(
          PullRequest.newBuilder().setMaxMessages(batchSize).setSubscription(subscriptionName.toString()).build())
    }).map(response => Chunk.from(response.getReceivedMessagesList().asScala))
      .recover { case _: DeadlineExceededException =>
        // no messages were available during the configured waiting time
        Chunk.empty
      }
      .flatMap { (msgs: Chunk[ReceivedMessage]) =>
        msgs.traverseFilter[F, ReceivedMessage] { msg =>
          val attrs = msg.getMessage().getAttributesMap().asScala
          F.realTimeInstant.flatMap { now =>
            attrs.get(delayAttribute) match {
              case Some(ToInstant(until)) if until.isAfter(now) =>
                wrapFuture(
                  F.delay(
                    subscriber
                      .modifyAckDeadlineCallable()
                      .futureCall(
                        ModifyAckDeadlineRequest
                          .newBuilder()
                          .addAckIds(msg.getAckId())
                          .setSubscription(subscriptionName.toString())
                          .setAckDeadlineSeconds(time.Duration.between(now, until).getSeconds().toInt)
                          .build()))).as(None)
              case _ => F.pure(Some(msg))
            }
          }
        }
      }
      .flatMap { (msgs: Chunk[ReceivedMessage]) =>
        msgs
          .traverse { msg =>
            deserializer
              .deserializeF[F](msg.getMessage().getData().toStringUtf8())
              .memoize
              .map(new PubSubMessageContext(subscriber, subscriptionName, msg, lockTTLSeconds, _, queueName))
          }
      }
      .widen[Chunk[MessageContext[F, T]]]
      .adaptError(makePullQueueException(_, queueName))

}
