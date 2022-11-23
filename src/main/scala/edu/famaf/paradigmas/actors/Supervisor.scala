package edu.famaf.paradigmas.actors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.Signal
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.Behaviors
import scala.util.{Success, Failure}
import scala.concurrent.duration._
import akka.util.Timeout

import edu.famaf.paradigmas.Subscription



object Supervisor {
  def apply(): Behavior[SupervisorCommand] = Behaviors.setup(context => new Supervisor(context))

  sealed trait SupervisorCommand
  final case class Stop() extends SupervisorCommand
  final case class ReceiveSubscriptions(
    subs: List[Subscription]
    ) extends SupervisorCommand
  final case class PrintData(
    supervisor: ActorRef[SupervisorCommand]
    ) extends SupervisorCommand
  final case class PrintUrlData(urlTemplate: String, msg: String) extends SupervisorCommand
  final case class UrlManagerReciveResponse(
    urlTemplate: String,
    urlManager: ActorRef[UrlManager.UrlManagerCommand]
    ) extends SupervisorCommand
  final case class UrlManagerFailedMessage(error_msg: String) extends SupervisorCommand
}

class Supervisor(context: ActorContext[Supervisor.SupervisorCommand])
    extends AbstractBehavior[Supervisor.SupervisorCommand](context) {
  context.log.info("Supervisor Started")

  implicit val timeout: Timeout = 3.seconds
  private var urlToUrlManager = Map.empty[String, ActorRef[UrlManager.UrlManagerCommand]]

  import Supervisor._

  override def onMessage(msg: SupervisorCommand): Behavior[SupervisorCommand] = {
    msg match {
      case ReceiveSubscriptions(subs) => {
        subs.map { 
          sub =>
            val urlManager = context.spawn(UrlManager(), sub.name)
            context.ask(urlManager, replyTo => UrlManager.ReceiveSubscription(sub, replyTo)) {
              case Success(UrlManager.UrlManagerResponseMessage(urlTemplate)) => {
                UrlManagerReciveResponse(urlTemplate, urlManager)
              }
              case Failure(e) => UrlManagerFailedMessage(e.getMessage)
            }
        }
        Behaviors.same
      }
      case Stop() => Behaviors.stopped
      case UrlManagerReciveResponse(urlTemplate, urlManager) =>
        urlToUrlManager += urlTemplate -> urlManager
        Behaviors.same
      case UrlManagerFailedMessage(error_msg) =>
        context.log.error(error_msg)
        Behaviors.same
      case PrintData(supervisor) => {
        urlToUrlManager.map { 
          case (urlTemplate, urlManager) =>
            context.ask(urlManager, replyTo => UrlManager.GetUrlData(replyTo, supervisor)) {
              case Success(UrlManager.UrlManagerResponseMessage(msg)) => {
                PrintUrlData(urlTemplate, msg)
              }
              case Failure(e) => UrlManagerFailedMessage(e.getMessage)
            }
        }
        Behaviors.same
      }
      case PrintUrlData(urlTemplate, msg) =>
        context.log.info(msg)
        Behaviors.same
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[SupervisorCommand]] = {
    case PostStop =>
      context.log.info("Supervisor Stopped")
      this
  }
}