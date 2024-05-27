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
 * @param messageTTL the time a message is guaranteed to stay in the queue before being discarded by the underlying system
 * @param lockTTL the time a message is locked (or leased) upon reception by a subscriber before being eligible to redelivery
 * @param deadletter the dead-letter queue configuration if any
 */
final case class QueueConfiguration(
  messageTTL: FiniteDuration,
  lockTTL: FiniteDuration,
  deadletter: Option[DeadletterQueueConfiguration])
