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



object Supervisor {
  def apply(): Behavior[SupervisorCommand] = Behaviors.setup(context => new Supervisor(context))

  sealed trait SupervisorCommand
  final case class Stop() extends SupervisorCommand
  final case class ReceiveSubscriptions(
    subs: List[Subscription],
    supervisor: ActorRef[SupervisorCommand],
    ) extends SupervisorCommand
}

class Supervisor(context: ActorContext[Supervisor.SupervisorCommand])
    extends AbstractBehavior[Supervisor.SupervisorCommand](context) {
  context.log.info("Supervisor Started")

  import Supervisor._

  override def onMessage(msg: SupervisorCommand): Behavior[SupervisorCommand] = {
    msg match {
      case ReceiveSubscriptions(subs, supervisor) => {
        subs.map { 
          sub =>
            val urlmanager = context.spawn(UrlManager(), sub.name)
            urlmanager ! UrlManager.ReceiveSubscription(sub, urlmanager, supervisor)
        }
        Behaviors.same
      }
      case Stop() => Behaviors.stopped
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[SupervisorCommand]] = {
    case PostStop =>
      context.log.info("Supervisor Stopped")
      this
  }
}