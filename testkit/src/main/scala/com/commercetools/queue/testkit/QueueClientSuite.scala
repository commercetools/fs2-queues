package com.commercetools.queue.testkit

import cats.effect.std.{Env, Random}
import cats.effect.{IO, Resource, SyncIO}
import com.commercetools.queue.QueueClient
import munit.Location
import munit.catseffect.IOFixture

import scala.concurrent.duration._

/**
 * This suite tests that the basic features of a [[com.commercetools.queue.QueueClient QueueClient]] are properly
 * implemented for a concrete client.
 * This is used in integration tests for the various implemented queue providers.
 */
abstract class QueueClientSuite
  extends QueueAdministrationSuite
    with QueueStatisticsSuite
    with QueuePublisherSuite
    with QueueSubscriberSuite {
  def optString(varName: String): IO[Option[String]] =
    Env[IO].get(varName)
  def string(varName: String): IO[String] =
    optString(varName).flatMap(_.map(IO.pure).getOrElse(IO.raiseError(new RuntimeException(s"'$varName' is required"))))
  def optBoolean(varName: String): IO[Option[Boolean]] =
    optString(varName).map(_.map(_.toBoolean))
  def booleanOrDefault(varName: String, default: Boolean): IO[Boolean] =
    optBoolean(varName).map(_.getOrElse(default))

  override def munitIOTimeout: Duration = 10.minute

  /** Override these if the given provider is not supporting these features */
  val queueUpdateSupported: Boolean = true
  val messagesStatsSupported: Boolean = true
  val inFlightMessagesStatsSupported: Boolean = true
  val delayedMessagesStatsSupported: Boolean = true

  final val originalMessageTTL: FiniteDuration = 10.minutes
  final val originalLockTTL: FiniteDuration = 2.minutes

  /** Provide a way to acquire a queue client for the provider under test. */
  def client: Resource[IO, QueueClient[IO]]

  final val clientFixture: IOFixture[QueueClient[IO]] = ResourceSuiteLocalFixture("queue-client", client)
  final lazy val withQueue: SyncIO[FunFixture[String]] =
    ResourceFunFixture(
      Resource.make(
        IO.randomUUID
          .map(uuid => s"queue-$uuid")
          .flatTap { queueName =>
            clientFixture().administration
              .create(queueName, originalMessageTTL, originalLockTTL)
          })(queueName => clientFixture().administration.delete(queueName)))
  final override def munitFixtures: List[IOFixture[QueueClient[IO]]] = List(clientFixture)

  final def randomMessages(n: Int): IO[List[(String, Map[String, String])]] = for {
    random <- Random.scalaUtilRandom[IO]
    size <- random.nextIntBounded(n - 1).map(_ + 1) // > 0
  } yield messages(size)

  final def messages(n: Int): List[(String, Map[String, String])] =
    List
      .range(0, n)
      .map(i => (i.toString, Map(s"metadata-$i-key" -> s"$i-value", s"metadata-$i-another-key" -> "another-value")))

  def eventuallyIO[A, B](
    obtained: IO[A],
    returns: B,
    clue: => Any = "values are not the same",
    retries: Int = 200,
    delay: FiniteDuration = 5.second
  )(implicit
    loc: Location,
    ev: B <:< A
  ): IO[Unit] = for {
    t <- IO.realTimeInstant
    _ <- assertIO(obtained, returns, clue).handleErrorWith(err =>
      if (retries > 0)
        IO.println(s" ${t.toString} - retrying assertion after $delay, remaining retries: $retries") >>
          IO.sleep(delay) >>
          eventuallyIO(obtained, returns, clue, retries - 1, delay)
      else IO.raiseError(err))
  } yield ()

  def eventuallyBoolean(
    cond: IO[Boolean],
    clue: => Any = "values are not the same",
    retries: Int = 200,
    delay: FiniteDuration = 5.second
  )(implicit
    loc: Location
  ): IO[Unit] = for {
    t <- IO.realTimeInstant
    _ <- assertIOBoolean(cond, clue).handleErrorWith(err =>
      if (retries > 0)
        IO.println(s" ${t.toString} - retrying assertion after $delay, remaining retries: $retries") >>
          IO.sleep(delay) >>
          eventuallyBoolean(cond, clue, retries - 1, delay)
      else IO.raiseError(err))
  } yield ()

}
