package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import messages._

import scala.concurrent.Future

/**
  * Passes the reference to other actors as a constructor parameter.
  *
  * Uses the actor's dispatcher as an ExecutionContext against which to execute futures.
  *
  * Created by carlos on 27/12/16.
  */
class TweetReachComputer(userFollowersCounter: ActorRef, storage: ActorRef) extends Actor with ActorLogging {

  // Sets up a cache to store the currently computed follower counts for a retweet
  var followerCountsByRetweet = Map.empty[FetchedRetweet, List[FollowerCount]]

  override def receive = {
    case ComputeReach(tweetId) =>
      log.info("Starting to compute tweet reach for tweet {}", tweetId)
      // Fetches the retweets from Twitter and acts upon the completion of this future result
      fetchRetweets(tweetId, sender()).map { fetchedRetweets =>
        followerCountsByRetweet = followerCountsByRetweet + (fetchedRetweets -> List.empty)
        fetchedRetweets.retweeters.foreach { rt =>
          // Asks for the followers count of each user that retweeted the tweet
          userFollowersCounter ! FetchFollowerCount(tweetId, rt)
        }
      }

    case count @  FollowerCount(tweetId, _, _) =>
      log.info("Received followers count for tweet {}", tweetId)
      fetchedRetweetsFor(tweetId).foreach { fetchedRetweets =>
        updateFollowersCount(tweetId, fetchedRetweets, count)
      }

    case ReachStored(tweetId) =>
      followerCountsByRetweet.keys.find(_.tweetId == tweetId).foreach { key =>
        // Removes the state once the score has been persisted
        followerCountsByRetweet = followerCountsByRetweet.filterNot(_._1 == key)
      }
  }


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


  def fetchRetweets(tweetId: BigInt, client: ActorRef): Future[FetchedRetweet] = ???


  override def unhandled(message: Any): Unit = {
    log.warning("Unhandled message {} message from {}", message, sender())
    super.unhandled(message)
  }

}



object TweetReachComputer {
  def props(userFollowersCounter: ActorRef, storage: ActorRef) =
    Props(classOf[TweetReachComputer], userFollowersCounter, storage)
}
