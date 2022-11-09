package edu.famaf.paradigmas.actors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.Signal
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.LoggerOps

import edu.famaf.paradigmas.Subscription
import edu.famaf.paradigmas.actors.Supervisor._


object UrlManager {
  def apply(): Behavior[UrlManagerCommand] = Behaviors.setup(context => new UrlManager(context))

  sealed trait UrlManagerCommand
  final case class Stop() extends UrlManagerCommand
  final case class ReceiveSubscription(
    sub: Subscription,
    urlmanager: ActorRef[UrlManagerCommand],
    supervisor: ActorRef[SupervisorCommand]
  ) extends UrlManagerCommand
}


class UrlManager(context: ActorContext[UrlManager.UrlManagerCommand])
    extends AbstractBehavior[UrlManager.UrlManagerCommand](context) {
  context.log.info("UrlManager Started")

  import UrlManager._

  override def onMessage(msg: UrlManagerCommand): Behavior[UrlManagerCommand] = {
    msg match {
      case Stop() => Behaviors.stopped
      case ReceiveSubscription(sub, urlmanager, supervisor) => {
        sub.feeds.map { 
          feedword => 
            val url: String = sub.url.replace("%s", feedword)
            val feed = context.spawn(Feed(), url.replace('/', '-'))
            feed ! Feed.ReceiveSubscriptionFeed(url, feed, urlmanager)
        }
        Behaviors.same
      }
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[UrlManagerCommand]] = {
    case PostStop =>
      context.log.info("UrlManager Stopped")
      this
  }
}