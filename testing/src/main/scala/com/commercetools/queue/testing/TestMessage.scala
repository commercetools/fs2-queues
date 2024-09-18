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

package com.commercetools.queue.testing

import cats.Order

import java.time.Instant

final case class TestMessage[T](payload: T, enqueuedAt: Instant, metadata: Map[String, String] = Map.empty)

object TestMessage {

  implicit def order[T]: Order[TestMessage[T]] = Order.by(_.enqueuedAt.toEpochMilli())

}
