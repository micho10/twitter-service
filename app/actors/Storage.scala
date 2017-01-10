package actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.ConnectionException
import messages.StoreReach
import org.joda.time.DateTime
//import reactivemongo.

/**
  * Created by carlos on 27/12/16.
  */
class Storage extends Actor with ActorLogging {

  val Database = "twitterService"
  val ReachCollection = "ComputedReach"

  implicit val executionContect = context.dispatcher

  val driver: MongoDriver = new MongoDriver()
  var connection: MongoConnection = _
  var db: DefaultDB = _
  var collection: BSONCollection = _
  obtainConnection()

  /**
    * Overrides the postRestart handler to reinitialize the connection after restart, if necessary
    *
    * @param reason
    */
  override def postRestart(reason: Throwable): Unit = {
    reason match {
        // Handles the case where you've restarted because of a connection exception
      case ce: ConnectionException =>
        // try to obtain a brand new connection
        obtainConnection()
    }
    super.postRestart(reason)
  }

  /**
    * Tears down connection and driver instances when the actor is stopped
    */
  override def postStop(): Unit = {
    connection.close()
    driver.close()
  }

  override def receive = {
    case StoreReach(tweetId, score) => // TODO
  }

  override def unhandled(message: Any): Unit = {
    log.warning("Unhandled message {} message from {}", message, sender())
    super.unhandled(message)
  }

  private def obtainConnection(): Unit = {
    // Declares MongoConnection as the state of the actor
    connection = driver.connection(List("Localhost"))
    db = connection.db(Database)
    collection = db.collection[BSONCollection](ReachCollection)
  }

}


case class StoredReach(when: DateTime, tweetItd: BigInt, score: Int)


object storage {
  def props = Props[Storage]
}
