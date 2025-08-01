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
import com.google.pubsub.v1.SubscriptionName

case class PubSubConfig(subscriptionNamePrefix: Option[String], subscriptionNameSuffix: Option[String]) {
  def subscriptionName(project: String, name: String): SubscriptionName =
    SubscriptionName.of(project, subscriptionNamePrefix.getOrElse("") + name + subscriptionNameSuffix.getOrElse(""))
}

object PubSubConfig {
  val default: PubSubConfig = PubSubConfig(Some("fs2-queue-"), Some("-sub"))
}
