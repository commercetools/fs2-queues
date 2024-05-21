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

class PubSubPusher[F[_], T](
  val queueName: String,
  topicName: TopicName,
  publisher: PublisherStub
)(implicit
  F: Async[F],
  serializer: Serializer[T])
  extends QueuePusher[F, T] {

  private def makeMessage(payload: T, waitUntil: Option[Instant]): F[PubsubMessage] =
    F.delay {
      val builder = PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(serializer.serialize(payload)))
      waitUntil.foreach(waitUntil => builder.putAttributes(delayAttribute, waitUntil.toString()))
      builder.build
    }

  override def push(message: T, delay: Option[FiniteDuration]): F[Unit] =
    (for {
      waitUntil <- delay.traverse(delay => F.realTimeInstant.map(_.plusMillis(delay.toMillis)))
      msg <- makeMessage(message, waitUntil)
      _ <- wrapFuture(
        F.delay(
          publisher
            .publishCallable()
            .futureCall(PublishRequest.newBuilder().addMessages(msg).setTopic(topicName.toString()).build())))
    } yield ())
      .adaptError(makePushQueueException(_, queueName))

  override def push(messages: List[T], delay: Option[FiniteDuration]): F[Unit] =
    (for {
      waitUntil <- delay.traverse(delay => F.realTimeInstant.map(_.plusMillis(delay.toMillis)))
      msgs <- messages.traverse(makeMessage(_, waitUntil))
      _ <- wrapFuture(
        F.delay(publisher
          .publishCallable()
          .futureCall(PublishRequest.newBuilder().addAllMessages(msgs.asJava).setTopic(topicName.toString()).build())))
    } yield ())
      .adaptError(makePushQueueException(_, queueName))

}
