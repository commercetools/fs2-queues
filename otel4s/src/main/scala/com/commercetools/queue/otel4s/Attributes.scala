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

import org.typelevel.otel4s.Attribute

object Attributes {
  final val send = Attribute("method", "send")
  final val receive = Attribute("method", "receive")
  final val create = Attribute("method", "create")
  final val delete = Attribute("method", "delete")
  final val exist = Attribute("method", "exist")
  final val ack = Attribute("method", "ack")
  final val nack = Attribute("method", "nack")
  final val extendLock = Attribute("method", "extendLock")

  final val success = Attribute("outcome", "success")
  final val failure = Attribute("outcome", "failure")
  final val cancelation = Attribute("outcome", "cancelation")
}
