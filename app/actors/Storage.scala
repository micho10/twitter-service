package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.ConnectionException
import messages.{ReachStored, StoreReach}
import org.joda.time.DateTime
//import reactivemongo.

/**
  * Created by carlos on 27/12/16.
  */
class Storage extends Actor with ActorLogging {

  val Database = "twitterService"
  val ReachCollection = "ComputedReach"

  implicit val executionContext = context.dispatcher

  val driver: MongoDriver = new MongoDriver()
  var connection: MongoConnection = _
  var db: DefaultDB = _
  var collection: BSONCollection = _
  obtainConnection()

  // Keeps track of the identifiers you're currently trying to write
  var currentWrites = Set.empty[BigInt]


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
    case StoreReach(tweetId, score) =>
      log.info("Storing reach for tweet {}", tweetId)
      // Checks whether you're already trying to write the score for this tweet, and only goes ahead if you're not
      if (!currentWrites.contains(tweetId)) {
        // Adds the tweet identifier to the set of current writes prior to saving it
        currentWrites = currentWrites + tweetId
        val originalSender = sender()
        collection
          .insert(StoredReach(DateTime.now, tweetId, score))
          .map { lastError =>
            LastStorageError(lastError, tweetId, originalSender)
          }.recover {
            // Removes the tweet identifier from the set of current writes in the case of failure
            case _ => currentWrites = currentWrites - tweetId
        } pipeTo self
      }
    case LastStorageError(error, tweetId, client) =>
      if (error.inError) {
        // Removes the tweet identifier from the set of current writes in the case of write error
        currentWrites = currentWrites - tweetId
      } else {
        client ! ReachStored(tweetId)
      }
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

  case class LastStorageError(error: LastError, tweetId: BigInt, client: ActorRef)
}
