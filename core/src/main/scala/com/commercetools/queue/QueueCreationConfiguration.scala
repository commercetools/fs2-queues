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

package com.commercetools.queue

import scala.concurrent.duration.FiniteDuration

/**
 * Configuration provided upon queue creation.
 *
 * @param messageTTL the time a message is kept in the queue before being discarded
 * @param lockTTL the time a message is locked (or leased) after having been delivered
 *                to a consumer and before being made available to other again
 * @param deadletter whether to create a dead letter queue associated to this queue
 *                   with the configured amount of delivery tries before moving a message
 *                   to the dead letter queue
 */
final case class QueueCreationConfiguration(
  messageTTL: FiniteDuration,
  lockTTL: FiniteDuration,
  deadletter: Option[DeadletterQueueCreationConfiguration] = None)
