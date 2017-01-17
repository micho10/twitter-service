package actors

import actors.StatisticsProvider.ServiceUnavailable
import akka.actor.{Actor, ActorLogging, Props}
import akka.dispatch.ControlMessage
import messages.{ComputeReach, FollowerCount}
import org.joda.time.{DateTime, Interval}
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.ws.WS

import scala.concurrent.Future


/**
  * Created by carlos on 27/12/16.
  */
class UserFollowersCounter extends Actor with ActorLogging with TwitterCredentials {

  override def receive = {
    case TwitterRateLimitReached(reset) =>
      // Schedules a message to remind you whne you've reached the window reset
      context.system.scheduler.scheduleOnce(
        new Interval(DateTime.now, reset).toDurationMillis.millis, self, ResumeService
      )
      context.become({
        case reach @ ComputeReach(_) =>
          // Rejects all incoming messages
          sender() ! ServiceUnavailable
        case ResumeService =>
          // Resumes the service by cancelling the temporary behaviour
          context.unbecome()
      })
  }


  override def unhandled(message: Any): Unit = {
    log.warning("Unhandled message {} message from {}", message, sender())
    super.unhandled(message)
  }


  val LimitRemaining  = "X-Rate-Limit-Remaining"
  val LimitReset      = "X-Rate-Limit-Reset"


  private def fetchFollowerCount(tweetId: BigInt, userId: BigInt): Future[FollowerCount] = {
    credentials.map {
      case (consumerKey, requestToken) =>
        WS.url("https://api.twitter.com/1.1/users/show.json")
          .sign(OAuthCalculator(consumerKey, requestToken))
          .withQueryString("user_id" -> userId.toString)
          .get().map { response =>
            if (response.status == 200) {

              val rateLimit = for {
                remaining <- response.header(LimitRemaining)
                reset     <- response.header(LimitReset)
              } yield (remaining.toInt, new DateTime(reset.toLong * 1000))

              rateLimit.foreach { case (remaining, reset) =>
                log.info("Rate limit: {} requestes remaining, window resets at {}", remaining, reset)
                if (remaining < 50) Thread.sleep(10000)
                if (remaining < 10) context.parent ! TwitterRateLimitReached(reset)
              }

              val count = (response.json \ "followers_count").as[Int]
              FollowerCount(tweetId, userId, count)
            } else throw new RuntimeException(s"Could not retrieve followers count for user $userId")
        }
    }.getOrElse {
      Future.failed(new RuntimeException("You did not configure the Twitter credentials correctly"))
    }
  }

}



object userFollowersCounter {
  def props = Props[UserFollowersCounter]
}



case class TwitterRateLimitReached(reset: DateTime) extends ControlMessage
case class FollowerCountUnavailable(tweetId: BigInt, user: BigInt)
case object UserFollowersCounterUnavailable extends ControlMessage
case object UserFollowersCounterAvailable extends ControlMessage

case object ResumeService
