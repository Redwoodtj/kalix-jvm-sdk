/* This code was generated by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */
package com.example.actions

import kalix.scalasdk.action.Action
import kalix.scalasdk.action.ActionCreationContext
import com.example.domain.CounterState
import com.google.protobuf.empty.Empty

class CounterStateSubscriptionAction(creationContext: ActionCreationContext)
    extends AbstractCounterStateSubscriptionAction {

  override def onUpdateState(counterState: CounterState): Action.Effect[Empty] =
    effects.noReply

}
