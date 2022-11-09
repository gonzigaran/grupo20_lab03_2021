package edu.famaf.paradigmas.actors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.Signal
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.LoggerOps

import edu.famaf.paradigmas.actors.UrlManager._
import edu.famaf.paradigmas.parser.RSSParser

object Feed {
  def apply(): Behavior[FeedCommand] = Behaviors.setup(context => new Feed(context))

  sealed trait FeedCommand
  final case class Stop() extends FeedCommand
  final case class ReceiveSubscriptionFeed(
    url: String,
    feed: ActorRef[FeedCommand],
    urlmanager: ActorRef[UrlManagerCommand]
  ) extends FeedCommand
}

class Feed(context: ActorContext[Feed.FeedCommand])
    extends AbstractBehavior[Feed.FeedCommand](context) {
  context.log.info("Feed Started")

  import Feed._

  override def onMessage(msg: FeedCommand): Behavior[FeedCommand] = {
    msg match {
      case Stop() => Behaviors.stopped
      case ReceiveSubscriptionFeed(url, feed, urlmanager) => {
          context.log.info("Feed: {}", url)
          val parser = new RSSParser()
          val feed_content = parser.readText(url)
          context.log.info(feed_content.mkString(" "))
          Behaviors.same
      }
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[FeedCommand]] = {
    case PostStop =>
      context.log.info("Feed Stopped")
      this
  }
}