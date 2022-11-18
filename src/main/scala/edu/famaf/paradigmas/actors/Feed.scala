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
    replyTo: ActorRef[FeedResponse]
  ) extends FeedCommand
  final case class GetFeedData(
    replyTo: ActorRef[FeedResponse]
  ) extends FeedCommand

  sealed trait FeedResponse
  final case class FeedResponseMessage(msg: String) extends FeedResponse
}

class Feed(context: ActorContext[Feed.FeedCommand])
    extends AbstractBehavior[Feed.FeedCommand](context) {
  context.log.info("Feed Started")
  
  private var url : String = ""

  import Feed._

  override def onMessage(msg: FeedCommand): Behavior[FeedCommand] = {
    msg match {
      case Stop() => Behaviors.stopped
      case ReceiveSubscriptionFeed(urlMessage, feed, replyTo) => {
          // val parser = new RSSParser()
          // val feed_content = parser.readText(url)
          // context.log.info(feed_content.mkString(" "))
          url = urlMessage
          replyTo ! FeedResponseMessage(url)
          Behaviors.same
      }
      case GetFeedData(replyTo) => {
          val parser = new RSSParser()
          val feed_content = parser.readText(url).mkString(" ")
          replyTo ! FeedResponseMessage(feed_content)
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