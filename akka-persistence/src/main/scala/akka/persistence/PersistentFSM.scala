package akka.persistence

import akka.actor.FSM
import akka.routing.{Deafen, Listen}

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

trait PersistentFSM[S, D] extends PersistentActor with FSM[S, D] {
  override private[akka] def applyState(nextState: State): Unit = {
    sync(nextState, super.applyState(_))
  }

  /**
   * Insert a new StateFunction at the end of the processing chain for the
   * given state during recovery. If the stateTimeout parameter is set, entering this state
   * without a differing explicit timeout setting will trigger a StateTimeout
   * event; the same is true when using #stay.
   *
   * @param stateName designator for the state
   * @param stateFunction partial function describing response to input
   */
  final def whenRecovering(stateName: S)(stateFunction: StateFunction): Unit =
    registerRecovery(stateName, stateFunction)

  private val recoveryStateFunctions = mutable.Map[S, StateFunction]()

  private def registerRecovery(name: S, function: StateFunction): Unit = {
    if (recoveryStateFunctions contains name) {
      recoveryStateFunctions(name) = recoveryStateFunctions(name) orElse function
    } else {
      recoveryStateFunctions(name) = function
    }
  }

  override final def receiveCommand = super[FSM].receive

  override final def receiveRecover: Receive = {
    case value if recoveryStateFunctions(stateName) isDefinedAt Event(value, stateData) ⇒ {
      makeTransition(recoveryStateFunctions(stateName)(Event(value, stateData)))
    }
  }

  override final def receive : Receive = receiveCommand
}


