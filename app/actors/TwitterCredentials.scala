package actors

import play.api.Play
import play.api.libs.oauth.{ConsumerKey, RequestToken}

/**
  * Created by carlos on 05/01/17.
  */
trait TwitterCredentials {

  protected def credentials = for {
    apiKey <- Play.configuration.getString("twitter.apiKey")
    apiSecret <- Play.configuration.getString("twitter.apiSecret")
    token <- Play.configuration.getString("twitter.token")
    tokenSecret <- Play.configuration.getString("twitter.tokenSecret")
  } yield (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))

}
