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
 * Interface that gives access to the queue administration capabilities.
 */
trait QueueAdministration[F[_]] {

  /**
   * Creates a queue with the given name and configuration.
   * If the configuration contains a `deadletter` element, a dead letter
   * queue is created and associated to the main queue with the configured
   * maximum delivery attempt.
   */
  def create(name: String, configuration: QueueCreationConfiguration): F[Unit]

  /**
   * Updates the queue with the given name, with provided message TTL and/or lock TTL.
   * Only the provided elements are updated, if a value is not provided, the previous value is kept.
   */
  def update(name: String, messageTTL: Option[FiniteDuration] = None, lockTTL: Option[FiniteDuration] = None): F[Unit]

  /** Returns the current configuration settings for the queue. */
  def configuration(name: String): F[QueueConfiguration]

  /** Deletes the queue with the given name. */
  def delete(name: String): F[Unit]

  /** Indicates whether the queue with the given name exists. */
  def exists(name: String): F[Boolean]

}
