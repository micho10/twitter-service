package actors

import actors.storage.LastStorageError
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.ConnectionException
import messages.{ReachStored, StoreReach}
import org.joda.time.DateTime
//import reactivemongo.


case class StoredReach(when: DateTime, tweetItd: BigInt, score: Int)


object StoredReach {

  implicit object BigIntHandler extends BSONDocumentReader[BigInt] with BSONDocumentWriter[BigInt] {
    def write(bigInt: BigInt): BSONDocument = BSONDocument(
      "signum"  -> bigInt.signum,
      "value"   -> BSONBinary(BigInt.toByteArray, Subtype.UserDefinedSubtype)
    )

    def read(doc: BSONDocument): BigInt = BigInt(
      doc.getAs[Int]("signum").get, {
        val buf = doc.getAs[BSONBinary]("value").get.value
        buf.readArray(buf.readable())
      })
  }

  implicit object StoredReachHandler extends BSONDocumentReader[StoredReach] with BSONDocumentWriter[StoredReach] {
    override def read(bson: BSONDocument): StoredReach = {
      val when = bson.getAs[BSONDateTime]("when").map(t => new DateTime(t.value)).get
      val tweetId = bson.getAs[BigInt]("tweet_id").get
      val score = bson.getAs[BigInt]("score").get
      StoredReach(when, tweetId, score)
    }

    override def write(r: StoredReach): BSONDocument = BSONDocument(
      "when"      -> BSONDateTime(r.when.getMillis),
      "tweetId"   -> r.tweetItd,
      "tweet_id"  -> r.tweetItd,
      "score"     -> r.score
    )
  }

}



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


  // Keeps track of the identifiers you're currently trying to write
  var currentWrites = Set.empty[BigInt]

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



object storage {

  def props = Props[Storage]

  case class LastStorageError(error: LastError, tweetId: BigInt, client: ActorRef)
}
