package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

/**
  * Created by carlos on 27/12/16.
  */
class TweetReachComputer(followersCounter: ActorRef, storage: ActorRef) extends Actor with ActorLogging {

  override def receive = {
    case message => // do nothing
  }

  override def unhandled(message: Any): Unit = {
    log.warning("Unhandled message {} message from {}", message, sender())
    super.unhandled(message)
  }

}


object TweetReachComputer {
  def props(followersCounter: ActorRef, storage: ActorRef) =
    Props(classOf[TweetReachComputer], followersCounter, storage)
}
