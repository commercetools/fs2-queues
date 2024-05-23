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

package com.commercetools.queue.pubsub

import cats.effect.{IO, Resource}
import com.commercetools.queue.QueueClient
import com.commercetools.queue.gcp.pubsub.PubSubClient
import com.commercetools.queue.testkit.QueueClientSuite
import com.google.api.gax.core.NoCredentialsProvider

class PubSubClientSuite extends QueueClientSuite {

  override val queueUpdateSupported = false

  override def client: Resource[IO, QueueClient[IO]] =
    PubSubClient("test-project", NoCredentialsProvider.create(), endpoint = Some("http://localhost:8042"))

}
