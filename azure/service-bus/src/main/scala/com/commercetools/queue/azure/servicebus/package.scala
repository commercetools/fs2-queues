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

package com.commercetools.queue.azure

import com.azure.core.exception.{ResourceExistsException, ResourceNotFoundException}
import com.commercetools.queue.{Action, CannotPullException, CannotPushException, MessageException, QueueAlreadyExistException, QueueDoesNotExistException, QueueException, UnknownQueueException}

package object servicebus {

  private[servicebus] def makeQueueException(t: Throwable, queueName: String): QueueException =
    t match {
      case _: ResourceNotFoundException => QueueDoesNotExistException(queueName, t)
      case _: ResourceExistsException => QueueAlreadyExistException(queueName, t)
      case t: QueueException => t
      case _ => UnknownQueueException(queueName, t)
    }

  private[servicebus] def makePushQueueException(t: Throwable, queueName: String): QueueException =
    new CannotPushException(queueName, makeQueueException(t, queueName))

  private[servicebus] def makePullQueueException(t: Throwable, queueName: String): QueueException =
    t match {
      case t: QueueException => t
      case _ => new CannotPullException(queueName, makeQueueException(t, queueName))
    }

  private[servicebus] def makeMessageException(t: Throwable, queueName: String, msgId: String, action: Action)
    : QueueException =
    t match {
      case t: QueueException => t
      case _ => new MessageException(msgId = msgId, action = action, inner = makeQueueException(t, queueName))
    }

}
