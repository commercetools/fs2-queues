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

package com.commercetools.queue.otel4s

import cats.effect.IO
import cats.effect.kernel.Resource
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.metrics.Counter
import org.typelevel.otel4s.sdk.metrics.data.{MetricData, MetricPoints, PointData}
import org.typelevel.otel4s.sdk.testkit.OpenTelemetrySdkTestkit

trait TestMetrics {

  def testkitCounter(counterName: String): Resource[IO, (OpenTelemetrySdkTestkit[IO], Counter[IO, Long])] =
    OpenTelemetrySdkTestkit.inMemory[IO]().evalMap { testkit =>
      for {
        id <- IO.randomUUID
        meter <- testkit.meterProvider.get(s"test-meter-$id")
        counter <- meter.counter[Long](counterName).create
      } yield (testkit, counter)
    }

}

final case class CounterDataPoint(value: Long, attributes: Attributes)
final case class CounterData(name: String, values: Vector[CounterDataPoint])
object CounterData {

  def fromMetricData(metric: MetricData): Option[CounterData] =
    metric.data match {
      case sum: MetricPoints.Sum =>
        Some(
          CounterData(
            metric.name,
            sum.points.collect { case point: PointData.LongNumber =>
              CounterDataPoint(point.value, point.attributes)
            }))
      case _ =>
        None
    }

}
