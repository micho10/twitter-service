package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

/**
  * Created by carlos on 27/12/16.
  */
class TweetReachComputer(followersCounter: ActorRef, storage: ActorRef) extends Actor with ActorLogging {

  override def receive = {
    case message => // do nothing
  }

}


object tweetReachComputer {
  def props(followersCounter: ActorRef, storage: ActorRef) =
    Props(classOf[TweetReachComputer], followersCounter, storage)
}
