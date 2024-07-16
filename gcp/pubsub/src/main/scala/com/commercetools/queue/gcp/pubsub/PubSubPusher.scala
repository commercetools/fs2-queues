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
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.monadError._
import cats.syntax.traverse._
import com.commercetools.queue.{QueuePusher, Serializer}
import com.google.cloud.pubsub.v1.stub.PublisherStub
import com.google.protobuf.ByteString
import com.google.pubsub.v1.{PublishRequest, PubsubMessage, TopicName}

import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

private class PubSubPusher[F[_], T](
  val queueName: String,
  topicName: TopicName,
  publisher: PublisherStub
)(implicit
  F: Async[F],
  serializer: Serializer[T])
  extends QueuePusher[F, T] {

  private def makeMessage(payload: T, metadata: Map[String, String], waitUntil: Option[Instant]): F[PubsubMessage] =
    F.delay {
      val builder = PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(serializer.serialize(payload)))
      builder.putAllAttributes(metadata.asJava)
      waitUntil.foreach(waitUntil => builder.putAttributes(delayAttribute, waitUntil.toString()))
      builder.build
    }

  override def push(message: T, metadata: Map[String, String], delay: Option[FiniteDuration]): F[Unit] =
    (for {
      waitUntil <- delay.traverse(delay => F.realTimeInstant.map(_.plusMillis(delay.toMillis)))
      msg <- makeMessage(message, metadata, waitUntil)
      _ <- wrapFuture(
        F.delay(
          publisher
            .publishCallable()
            .futureCall(PublishRequest.newBuilder().addMessages(msg).setTopic(topicName.toString()).build())))
    } yield ())
      .adaptError(makePushQueueException(_, queueName))

  override def push(messages: List[(T, Map[String, String])], delay: Option[FiniteDuration]): F[Unit] =
    (for {
      waitUntil <- delay.traverse(delay => F.realTimeInstant.map(_.plusMillis(delay.toMillis)))
      msgs <- messages.traverse { case (payload, metadata) => makeMessage(payload, metadata, waitUntil) }
      _ <- wrapFuture(
        F.delay(publisher
          .publishCallable()
          .futureCall(PublishRequest.newBuilder().addAllMessages(msgs.asJava).setTopic(topicName.toString()).build())))
    } yield ())
      .adaptError(makePushQueueException(_, queueName))

}
