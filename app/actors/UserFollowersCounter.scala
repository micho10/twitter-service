package actors

import akka.actor.{Actor, ActorLogging, Props}

/**
  * Created by carlos on 27/12/16.
  */
class UserFollowersCounter extends Actor with ActorLogging {

  override def receive = {
    case message => // do nothing
  }

  override def unhandled(message: Any): Unit = {
    log.warning("Unhandled message {} message from {}", message, sender())
    super.unhandled(message)
  }

}


object userFollowersCounter {
  def props = Props[UserFollowersCounter]
}
