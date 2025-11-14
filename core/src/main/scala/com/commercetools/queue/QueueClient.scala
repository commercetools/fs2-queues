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
 * The entry point to using queues.
 * A client will manage connection pools and has knowledge of the underlying queue system.
 * A client should be managed as a resource to cleanup connections when not need anymore.
 */
sealed trait QueueClient[F[_]] {

  /**
   * Name of the concrete underlying system
   */
  def systemName: String

  /**
   * Gives access to adminsitrative API.
   */
  def administration: QueueAdministration[F]

  /**
   * Gives access to queue statistics API.
   */
  def statistics(name: String): QueueStatistics[F]

  /**
   * Gives access to the publication API.
   */
  def publish[T: Serializer](name: String): QueuePublisher[F, T]

  /**
   * Gives access to the subscription API.
   */
  def subscribe[T: Deserializer](name: String): QueueSubscriber[F, T]

}

private[queue] trait UnsealedQueueClient[F[_]] extends QueueClient[F]
