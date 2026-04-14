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
import cats.syntax.monadError._
import com.commercetools.queue.{MalformedQueueConfigurationException, QueueStats, UnsealedQueueStatsFetcher}
import com.google.cloud.monitoring.v3.stub.MetricServiceStub
import com.google.monitoring.v3.{ListTimeSeriesRequest, Point, TimeInterval}
import com.google.protobuf.Timestamp
import com.google.pubsub.v1.SubscriptionName

import scala.jdk.CollectionConverters._

private class PubSubStatsFetcher[F[_]](
  val queueName: String,
  subscriptionName: SubscriptionName,
  client: MetricServiceStub
)(implicit F: Async[F])
  extends UnsealedQueueStatsFetcher[F] {

  override def fetch: F[QueueStats] =
    F.realTime
      .flatMap { now =>
        wrapFuture(F.delay {
          client
            .listTimeSeriesCallable()
            .futureCall(
              ListTimeSeriesRequest
                .newBuilder()
                .setName(s"projects/${subscriptionName.getProject()}")
                // we need to query at least one minute
                // https://stackoverflow.com/questions/68546947/google-cloud-metrics-get-pub-sub-metric-filtered-by-subscription-id
                .setInterval(TimeInterval
                  .newBuilder()
                  .setStartTime(Timestamp.newBuilder().setSeconds(now.toSeconds - 61).build())
                  .setEndTime(Timestamp.newBuilder().setSeconds(now.toSeconds).build())
                  .build())
                .setFilter(
                  s"""metric.type="pubsub.googleapis.com/subscription/num_undelivered_messages" AND resource.label.subscription_id = "${subscriptionName
                      .getSubscription()}"""")
                .build())

        })
          .adaptError(makeQueueException(_, queueName))
      }
      .flatMap { response =>
        if (response.getTimeSeriesCount == 0) F.pure(QueueStats(0, None, None))
        else {
          val datapoints: List[Point] = response.getTimeSeries(0).getPointsList().asScala.toList
          datapoints.sortBy(-_.getInterval().getEndTime().getSeconds()).headOption match {
            case Some(value) =>
              F.pure(QueueStats(value.getValue().getInt64Value().toInt, None, None))
            case None =>
              F.raiseError(MalformedQueueConfigurationException(queueName, "messages", "<missing>"))
          }
        }
      }

}
