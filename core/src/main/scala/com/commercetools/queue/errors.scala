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

import cats.syntax.show._

/**
 * The base exception thrown by the clients.
 * It defines some well known common exception and avoid leaking the underlying
 * queue system exception model when failing.
 */
sealed abstract class QueueException(msg: String, inner: Throwable) extends Exception(msg, inner)

case class DeserializationException(body: String, inner: Throwable)
  extends QueueException(show"Something went wrong when deserializing '$body'", inner)

case class QueueDoesNotExistException(name: String, inner: Throwable)
  extends QueueException(show"Queue $name does not exist", inner)

case class QueueAlreadyExistException(name: String, inner: Throwable)
  extends QueueException(show"Queue $name does not exist", inner)

case class CannotPushException(name: String, inner: Throwable)
  extends QueueException(show"Cannot push messages to queue $name", inner)

case class CannotPullException(name: String, inner: Throwable)
  extends QueueException(show"Cannot pull messages from queue $name", inner)

case class MessageException(msgId: MessageId, action: Action, inner: Throwable)
  extends QueueException(show"Cannot $action message ${msgId.value}", inner)

case class UnknownQueueException(name: String, inner: Throwable)
  extends QueueException(show"Something went wrong when interacting with queue $name", inner)

case class MalformedQueueConfigurationException(name: String, attribute: String, raw: String, inner: Throwable = null)
  extends QueueException(show"Attribute $attribute of queue $name is malformed: $raw", inner)
