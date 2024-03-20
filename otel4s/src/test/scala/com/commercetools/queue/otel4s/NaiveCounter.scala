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

import cats.data.Chain
import cats.effect.{IO, Ref}
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.meta.InstrumentMeta
import org.typelevel.otel4s.metrics.Counter

class NaiveCounter(val records: Ref[IO, Chain[(Long, List[Attribute[_]])]]) extends Counter[IO, Long] {

  override val backend: Counter.Backend[IO, Long] = new Counter.LongBackend[IO] {

    override val meta: InstrumentMeta[IO] = InstrumentMeta.enabled

    override def add(value: Long, attributes: Attribute[_]*): IO[Unit] =
      records.update(_.append((value, attributes.toList)))

  }

}

object NaiveCounter {

  def create: IO[NaiveCounter] =
    Ref[IO]
      .of(Chain.empty[(Long, List[Attribute[_]])])
      .map(new NaiveCounter(_))

}
