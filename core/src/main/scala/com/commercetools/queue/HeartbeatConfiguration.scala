package com.commercetools.queue

import scala.concurrent.duration.FiniteDuration

final case class HeartbeatConfiguration(every: FiniteDuration, maxRenewals: Int)
