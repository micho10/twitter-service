package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.pattern.pipe
import messages._
import play.api.libs.json.JsArray
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.ws.WS

import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * Passes the reference to other actors as a constructor parameter.
  *
  * Uses the actor's dispatcher as an ExecutionContext against which to execute futures.
  *
  * Created by carlos on 27/12/16.
  */
class TweetReachComputer(userFollowersCounter: ActorRef, storage: ActorRef) extends Actor with ActorLogging
  with TwitterCredentials {

  // Sets up a cache to store the currently computed follower counts for a retweet
  var followerCountsByRetweet = Map.empty[FetchedRetweet, List[FollowerCount]]

  implicit val executionContext = context.dispatcher

  // Defines a case class to hold the context of the failure
  case class RetweetFetchingFailed(tweetId: BigInt, cause: Throwable, client: ActorRef)

  // Initializes the scheduler for resending unacknowledged messages every 20 seconds
  val retryScheduler: Cancellable = context.system.scheduler.schedule(1.second, 20.seconds, self, ResendUnacknowledged)


  override def postStop(): Unit = {
    // Cancels the scheduler when the actor is stopped
    retryScheduler.cancel()
  }


  override def receive = {

    case ComputeReach(tweetId) =>
      // Fetches the retweets from Twitter and acts upon the completion of this future result
      log.info("Starting to compute tweet reach for tweet {}", tweetId)
      val originalSender = sender()
      // Handles the recovery of the failure of the future
      fetchRetweets(tweetId, sender()).recover{
        case NonFatal(t) =>
          // Wraps the cause of the failure together with some context in the case class designed for this purpose
          RetweetFetchingFailed(tweetId, t, originalSender)
        // Pipes the "safe" future
      } pipeTo self

    case fetchedRetweets: FetchedRetweet =>
      // Receives the result of the future
      followerCountsByRetweet += fetchedRetweets -> List.empty
      fetchedRetweets.retweeters.foreach { rt =>
        // Asks for the followers count of each user that retweeted the tweet
        userFollowersCounter ! FetchFollowerCount(fetchedRetweets.tweetId, rt)
      }

    case count @ FollowerCount(tweetId, _, _) =>
      log.info("Received followers count for tweet {}", tweetId)
      fetchedRetweetsFor(tweetId).foreach { fetchedRetweets =>
        updateFollowersCount(tweetId, fetchedRetweets, count)
      }

    case ReachStored(tweetId) =>
      followerCountsByRetweet.keys.find(_.tweetId == tweetId).foreach { key =>
        // Removes the state once the score has been persisted
        followerCountsByRetweet = followerCountsByRetweet.filterNot(_._1 == key)
      }

    case ResendUnacknowledged =>
      // Filters out the cases for which all counts have been received
      val unacknowledged = followerCountsByRetweet.filterNot {
        case (retweet, counts) =>
          retweet.retweeters.size != counts.size
      }
      unacknowledged.foreach {case (retweet, counts) =>
        val score = counts.map(_.followersCount).sum
        // Sends a new StoreReach message to the storage
        storage ! StoreReach(retweet.tweetId, score)
      }
  }


  case object ResendUnacknowledged
  case class FetchedRetweet(tweetId: BigInt, retweeters: List[String], client: ActorRef)


  def fetchedRetweetsFor(tweetId: BigInt) = followerCountsByRetweet.keys.find(_.tweetId == tweetId)


  def updateFollowersCount(tweetId: BigInt, fetchedRetweets: FetchedRetweet, count: FollowerCount) = {
    val existingCounts = followerCountsByRetweet(fetchedRetweets)
    // Updates the state of retrieved follower counts
    followerCountsByRetweet = followerCountsByRetweet.updated(
      fetchedRetweets, count :: existingCounts
    )

    val newCounts = followerCountsByRetweet(fetchedRetweets)

    // Checks if all follower counts were retrieved
    if (newCounts.length == fetchedRetweets.retweeters.length) {
      log.info("Received all retweeters followers count for tweet {}, computing sum", tweetId)
      val score = newCounts.map(_.followersCount).sum
      // Replies to the client with the final score
      fetchedRetweets.client ! TweetReach(tweetId, score)
      // Asks for the score to be persisted
      storage ! StoreReach(tweetId, score)
    }
  }


  def fetchRetweets(tweetId: BigInt, client: ActorRef): Future[FetchedRetweet] = {
    credentials.map {
      case (consumerKey, requestToken) =>
        WS.url("https://api.twitter.com/1.1/statuses/retweeters/ids.json")
          .sign(OAuthCalculator(consumerKey, requestToken))
          .withQueryString("id" -> tweetId.toString)
          .withQueryString("stringify_ids" -> "true")
          .get().map { response =>
          if (response.status == 200) {
            val ids = (response.json \ "ids").as[JsArray].value.map(v => BigInt(v.as[String])).toList
            FetchedRetweet(tweetId, ids, client)
          } else throw new RuntimeException(s"Could not retrieve details for Tweet $tweetId")
        }
    }.getOrElse{
      Future.failed(new RuntimeException("You did not configure the Twitter credentials correctly"))
    }
  }


  override def unhandled(message: Any): Unit = {
    log.warning("Unhandled message {} message from {}", message, sender())
    super.unhandled(message)
  }

}



object TweetReachComputer {
  def props(userFollowersCounter: ActorRef, storage: ActorRef) =
    Props(classOf[TweetReachComputer], userFollowersCounter, storage)
}
