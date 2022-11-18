package edu.famaf.paradigmas.actors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.Signal
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.LoggerOps
import scala.util.{Success, Failure}
import scala.concurrent.duration._
import akka.util.Timeout

import edu.famaf.paradigmas.Subscription
import edu.famaf.paradigmas.actors.Supervisor._


object UrlManager {
  def apply(): Behavior[UrlManagerCommand] = Behaviors.setup(context => new UrlManager(context))

  sealed trait UrlManagerCommand
  final case class Stop() extends UrlManagerCommand
  final case class ReceiveSubscription(
    sub: Subscription,
    replyTo: ActorRef[UrlManagerResponse]
  ) extends UrlManagerCommand
  final case class FeedReciveResponse(url: String, feed: ActorRef[Feed.FeedCommand]) extends UrlManagerCommand
  final case class FeedFailedMessage(error_msg: String) extends UrlManagerCommand
  final case class GetUrlData(
    replyTo: ActorRef[UrlManagerResponse],
    supervisor: ActorRef[SupervisorCommand]
  ) extends UrlManagerCommand
  final case class PrintFeedData(
    msg: String, 
    url: String, 
    supervisor: ActorRef[SupervisorCommand]
  ) extends UrlManagerCommand
  
  sealed trait UrlManagerResponse
  final case class UrlManagerResponseMessage(msg: String) extends UrlManagerResponse
}


class UrlManager(context: ActorContext[UrlManager.UrlManagerCommand])
    extends AbstractBehavior[UrlManager.UrlManagerCommand](context) {
  context.log.info("UrlManager Started")

  import UrlManager._

  implicit val timeout: Timeout = 3.seconds
  private var urlToFeed = Map.empty[String, ActorRef[Feed.FeedCommand]]

  override def onMessage(msg: UrlManagerCommand): Behavior[UrlManagerCommand] = {
    msg match {
      case Stop() => Behaviors.stopped
      case FeedReciveResponse(url, feed) =>
        urlToFeed += url -> feed
        Behaviors.same
      case FeedFailedMessage(error_msg) =>
        context.log.error(error_msg)
        Behaviors.same
      case ReceiveSubscription(sub, replyTo) => {
        sub.feeds.map { 
          feedword => 
            val url: String = sub.url.replace("%s", feedword)
            val feed = context.spawn(Feed(), url.replace('/', '-'))
            context.ask(feed, replyTo => Feed.ReceiveSubscriptionFeed(url, feed, replyTo)) {
              case Success(Feed.FeedResponseMessage(url)) => FeedReciveResponse(url, feed)
              case Failure(e) => FeedFailedMessage(e.getMessage)
            }
        }
        replyTo ! UrlManagerResponseMessage(sub.url)
        Behaviors.same
      }
      case GetUrlData(replyTo, supervisor) => {
        urlToFeed.map { 
          case (url, feed) =>
            context.ask(feed, replyTo => Feed.GetFeedData(replyTo)) {
              case Success(Feed.FeedResponseMessage(msg)) => PrintFeedData(msg, url, supervisor)
              case Failure(e) => FeedFailedMessage(e.getMessage)
            }
        }
        replyTo ! UrlManagerResponseMessage("UrlManager data complete")
        Behaviors.same
      }
      case PrintFeedData(msg, url, supervisor) => {
        supervisor ! Supervisor.PrintUrlData(url, msg)
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