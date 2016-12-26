package actors

import akka.actor.{Actor, ActorLogging, Props}

/**
  * Created by carlos on 26/12/16.
  *
  * Implements the Actor trait and mixes in the ActorLogging trait, which provides nonblocking logging capabilities
  */
class StatisticsProvider extends Actor with ActorLogging {

  // Implements the receive method, which is the only method an actor needs to implement
  def receive = {
    // Handles any kind of incoming message by literally doing nothing
    case message => // do nothing
  }

}


/**
  * Defines how the actor can be instantiated by providing the Actor's Props
  */
object StatisticsProvider {
  def props = Props[StatisticsProvider]
}
