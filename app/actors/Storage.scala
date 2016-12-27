package actors

import akka.actor.{Actor, ActorLogging, Props}

/**
  * Created by carlos on 27/12/16.
  */
class Storage extends Actor with ActorLogging {

  override def receive = {
    case message => // do nothing
  }

}


object storage {
  def props = Props[Storage]
}
