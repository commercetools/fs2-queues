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

package com.commercetools.queue.azure.servicebus

/** Represents a size in kibibytes (1 KiB = 1024 bytes). */
final class Size private (val kib: Int) extends AnyVal {

  /** Represents a size in mibibytes (1 MiB = 1024 KiB). */
  def mib: Int = kib / 1024

  /** Represents a size in gibibytes (1 GiB = 1024 MiB). */
  def gib: Int = mib / 1024
}

object Size {

  /**
   * Creates the size with the given amount of KiB.
   */
  def kib(kib: Int): Size = new Size(kib)

  /**
   * Creates the size with the given amount of MiB.
   * 1 MiB = 1024 Kib
   */
  def mib(mib: Int): Size = new Size(mib * 1024)

  /**
   * Creates the size with the given amount of GiB.
   * 1 GiB = 1024 Mib
   */
  def gib(gib: Int): Size = new Size(gib * 1024 * 1024)

}

/**
 * Global settings that will be used for new queues created by the library.
 *
 * @param partitioned Whether the new queues are partitioned (set to true if your namespace is partitioned)
 * @param newQueueSize The max size of created queues
 * @param maxMessageSize The max allowed size for messages
 */
final case class NewQueueSettings(
  partitioned: Option[Boolean],
  queueSize: Option[Size],
  maxMessageSize: Option[Size])

object NewQueueSettings {

  /**
   * The default settings. Default Azure defined values will be used.
   */
  val default: NewQueueSettings = NewQueueSettings(None, None, None)

}
