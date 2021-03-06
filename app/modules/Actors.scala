package modules

import javax.inject.Inject

import actors.StatisticsProvider
import akka.actor.{ActorSystem, Props}
import com.google.inject.AbstractModule
import play.libs.Akka

/**
  * Created by carlos on 26/12/16.
  *
  * Injects the ActorSystem in the module implementation so it can create actors.
  */
class Actors @Inject() (system: ActorSystem) extends ApplicationActors {
  Akka.system.actorOf(
    props = StatisticsProvider.props
      // Specifies the custom dispatcher you set up in the configuration
      .withDispatcher("control-aware-mailbox"),
    name = "statisticsProvider"
  )
}


/**
  * Defines a marker trait for the actor's module
  */
trait ApplicationActors


/**
  * Implements the actor's Guice module
  */
class ActorsModule extends AbstractModule {

  override def configure(): Unit = {
    // Defines the binding as eager, so it's initialized when the application is wired up and is available to any
    // component in the app without explicitly depending on it.
    bind(classOf[ApplicationActors])
      .to(classOf[Actors]).asEagerSingleton()
  }

}
