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

import cats.effect.Async
import cats.implicits.{catsSyntaxApplicativeError, toFlatMapOps, toFunctorOps}
import com.azure.messaging.servicebus.ServiceBusReceiverClient
import com.commercetools.queue.{Message, MessageId, UnsealedMessageBatch}
import fs2.Chunk

private class ServiceBusMessageBatch[F[_], T](
  payload: Chunk[ServiceBusMessageContext[F, T]],
  receiver: ServiceBusReceiverClient
)(implicit F: Async[F])
  extends UnsealedMessageBatch[F, T] {
  override def messages: Chunk[Message[F, T]] = payload

  override def ackAll: F[List[MessageId]] =
    payload.toList.foldLeft(F.pure(List[MessageId]())) { (accF, mCtx) =>
      accF.flatMap { acc =>
        F.pure(receiver.complete(mCtx.underlying))
          .as(acc)
          .handleError(_ => acc :+ MessageId(mCtx.underlying.getMessageId))
      }
    }

  override def nackAll: F[List[MessageId]] =
    payload.toList.foldLeft(F.pure(List[MessageId]())) { (accF, mCtx) =>
      accF.flatMap { acc =>
        F.pure(receiver.abandon(mCtx.underlying))
          .as(acc)
          .handleError(_ => acc :+ MessageId(mCtx.underlying.getMessageId))
      }
    }
}
