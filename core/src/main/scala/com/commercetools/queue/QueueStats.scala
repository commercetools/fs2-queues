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

/**
 * Basic statistics for the
 *
 * @param messages The *approximate* number of available messages currently in the queue
 * @param inflight The *approximate* number of messages in the queue that are in-flight (messages delivered to a subscriber but not ack'ed yet) if available
 * @param delayed The *approximate* number of delayed messages in the queue if available
 */
final case class QueueStats(messages: Int, inflight: Option[Int], delayed: Option[Int])
