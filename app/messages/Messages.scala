package messages

/**
  * Created by carlos on 28/12/16.
  */
class Messages {
}

case class ComputeReach(tweetId: BigInt)
case class TweetReach(tweetId: BigInt, score: Int)

case class FetchFollowerCount(tweetId: BigInt, user: String)
case class FollowerCount(tweetId: BigInt, user: String, followersCount: Int)

case class StoreReach(tweetId: BigInt, score: Int)
case class ReachStored(tweetId: BigInt)
