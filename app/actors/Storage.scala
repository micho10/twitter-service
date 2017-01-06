package actors

import akka.actor.{Actor, ActorLogging, Props}
//import reactivemongo.

/**
  * Created by carlos on 27/12/16.
  */
class Storage extends Actor with ActorLogging {

  val Database = "twitterService"
  val ReachCollection = "ComputedReach"

  implicit val executionContect = context.dispatcher

//  val driver: MongoDriver = new MongoDriver()
//  var connection: MongoConnection = _
//  var db: DefaultDB = _
//  var collection: BSONCollection = _

  override def receive = {
    case message => // do nothing
  }

  override def unhandled(message: Any): Unit = {
    log.warning("Unhandled message {} message from {}", message, sender())
    super.unhandled(message)
  }

}


object storage {
  def props = Props[Storage]
}
