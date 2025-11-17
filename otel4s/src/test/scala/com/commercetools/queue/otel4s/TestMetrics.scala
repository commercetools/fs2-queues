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
import munit.FunSuite
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.sdk.metrics.data.{MetricData, MetricPoints, PointData}
import org.typelevel.otel4s.sdk.testkit.OpenTelemetrySdkTestkit
import org.typelevel.otel4s.semconv.{MetricSpec, Requirement}

trait TestMetrics { self: FunSuite =>

  private[otel4s] val testkitMetrics: Resource[IO, (OpenTelemetrySdkTestkit[IO], QueueMetrics[IO])] =
    mkTestkitMetrics(Attributes.empty)

  private[otel4s] def mkTestkitMetrics(commonAttributes: Attributes) =
    OpenTelemetrySdkTestkit.inMemory[IO]().evalMap { testkit =>
      for {
        id <- IO.randomUUID
        meter <- testkit.meterProvider.get(s"test-meter-$id")
        metrics <- QueueMetrics[IO](commonAttributes)(IO.asyncForIO, meter)
      } yield (testkit, metrics)
    }

  private[otel4s] def specTest(metrics: List[MetricData], spec: MetricSpec): Unit = {
    val metric = metrics.find(_.name == spec.name)
    assert(
      metric.isDefined,
      s"${spec.name} metric is missing. Available [${metrics.map(_.name).mkString(", ")}]"
    )

    val clue = s"[${spec.name}] has a mismatched property. $metric"

    metric.foreach { md =>
      assertEquals(md.name, spec.name, clue)
      assertEquals(md.description, Some(spec.description), clue)
      assertEquals(md.unit, Some(spec.unit), clue)

      val required = spec.attributeSpecs
        .filter(_.requirement.level == Requirement.Level.Required)
        .map(_.key)
        .toSet

      val current = md.data.points.toVector
        .flatMap(_.attributes.map(_.key))
        .filter(key => required.contains(key))
        .toSet

      assertEquals(current, required, clue)
    }
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
