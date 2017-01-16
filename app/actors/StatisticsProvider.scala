package actors

import actors.StatisticsProvider.{ReviveStorage, ServiceUnavailable}
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.stream.ConnectionException
import messages.ComputeReach

/**
  * Created by carlos on 26/12/16.
  *
  * Implements the Actor trait and mixes in the ActorLogging trait, which provides nonblocking logging capabilities
  */
class StatisticsProvider extends Actor with ActorLogging {

  var reachComputer: ActorRef = _
  var storage: ActorRef = _
  var followersCounter: ActorRef = _

  /**
    * Child actors must be created inside this method of the parent actor to ensure that they'll be re-created
    * if the parent actor crashes. When a parent crashes all children are terminated as well.
    */
  override def preStart(): Unit = {
    log.info("Starting StatisticsProvider")

    // Actors created using the "context.actorOf" method become children of this actor.
    // All children are accessible through the "context.children" collection,
    // or by their name with the "context.child(childName)" method.
    followersCounter = context.actorOf(Props[UserFollowersCounter], name = "serFollowersCounter")
    storage = context.actorOf(Props[Storage], name = "storage")
    reachComputer = context.actorOf(TweetReachComputer.props(followersCounter, storage), name = "tweetReachComputer")

    // Registers to the lifecycle monitoring of the Storage child actor
    context.watch(storage)
  }

  // Implements the receive method, which is the only method an actor needs to implement
  override def receive = {
    // Handles any kind of incoming message by literally doing nothing
    case reach: ComputeReach =>
      reachComputer forward reach
    // Reacts to the termination. Because you only subscribed to the Storage actor notifications,
    // it must be that actor that terminated
    case Terminated(terminatedStorageRef) =>
      // Schedules to send ifself a ReviveStorage message after a minute
      context.system.scheduler.scheduleOnce(1.minute, self, ReviveStorage)
      // Switches to a newly defined behaviour
      context.become(storageUnavailable)
  }

  def storageUnavailable: Receive = {
    case ComputeReach(_) =>
      // Responds to ComputeReach requests by telling the client the service is unavailable
      sender() ! ServiceUnavailable
    case ReviveStorage =>
      // Revives the Storage child actor
      storage = context.actorOf(Props[Storage], name = "storage")
      // Switches back to the original behaviour
      context.unbecome()
  }

  override def unhandled(message: Any): Unit = {
    log.warning("Unhandled message {} message from {}", message, sender())
    super.unhandled(message)
  }

  override def supervisorStrategy: SupervisorStrategy =
    // Uses a OneForOneStrategy, retrying up to 3 times within 2 minutes before stopping the actor
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 2.minutes) {
      case _: ConnectionException =>
        // Restarts the actor if faced with a ConnectionException
        Restart
      case t: Throwable =>
        // Applies the default supervisor strategy for any other kind of failure, escalating it if that strategy
        // doesn't handle the failure
        super.supervisorStrategy.decider.applyOrElse(t, _ => Escalate)
    }

}


/**
  * Defines how the actor can be instantiated by providing the Actor's Props
  */
object StatisticsProvider {

  case object ServiceUnavailable
  case object ReviveStorage

  def props = Props[StatisticsProvider]
}
