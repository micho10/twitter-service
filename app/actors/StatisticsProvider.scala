package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

/**
  * Created by carlos on 26/12/16.
  *
  * Implements the Actor trait and mixes in the ActorLogging trait, which provides nonblocking logging capabilities
  */
class StatisticsProvider extends Actor with ActorLogging {

  var reachComputer: ActorRef = _
  var storage: ActorRef = _
  var followersCounter: ActorRef = _

  // Implements the receive method, which is the only method an actor needs to implement
  override def receive = {
    // Handles any kind of incoming message by literally doing nothing
    case message => // do nothing
  }

  /**
    * Child actors must be created inside this method of the parent actor to ensure that they'll be re-created
    * if the parent actor crashes. When a parent crashes all children are terminated as well.
    */
  override def preStart(): Unit = {
    log.info("Starting StatisticsProvider")

    // Actors created using the "context.actorOf" method become children of this actor.
    // All children are accessible through the "context.children" collection,
    // or by their name with the "context.child(childname)" method.
    followersCounter = context.actorOf(
      Props[UserFollowersCounter],
      name = "serFollowersCounter"
    )

    storage = context.actorOf(Props[Storage], name = "storage")

    reachComputer = context.actorOf(
      TweetReachComputer.props(followersCounter, storage),
      name = "tweetReachComputer"
    )
  }

}


/**
  * Defines how the actor can be instantiated by providing the Actor's Props
  */
object StatisticsProvider {
  def props = Props[StatisticsProvider]
}
