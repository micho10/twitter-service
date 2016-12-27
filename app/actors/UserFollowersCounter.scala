package actors

import akka.actor.{Actor, ActorLogging, Props}

/**
  * Created by carlos on 27/12/16.
  */
class UserFollowersCounter extends Actor with ActorLogging {

  override def receive = {
    case message => // do nothing
  }

}


object userFollowersCounter {
  def props = Props[UserFollowersCounter]
}
