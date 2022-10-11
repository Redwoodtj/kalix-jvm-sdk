/*
 * Copyright 2021 Lightbend Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kalix.springsdk.impl.action

import akka.NotUsed
import akka.stream.javadsl.Source
import com.google.protobuf.any.{ Any => ScalaPbAny }
import kalix.javasdk.action.Action
import kalix.javasdk.action.MessageEnvelope
import kalix.javasdk.impl.action.ActionRouter
import kalix.springsdk.impl.CommandHandler
import kalix.springsdk.impl.InvocationContext
import reactor.core.publisher.Flux

class ReflectiveActionRouter[A <: Action](action: A, commandHandlers: Map[String, CommandHandler])
    extends ActionRouter[A](action) {

  private def commandHandlerLookup(commandName: String) =
    commandHandlers.getOrElse(commandName, throw new RuntimeException(s"no matching method for '$commandName'"))

  override def handleUnary(commandName: String, message: MessageEnvelope[Any]): Action.Effect[_] = {

    val commandHandler = commandHandlerLookup(commandName)
    val context =
      InvocationContext(
        message.payload().asInstanceOf[ScalaPbAny],
        commandHandler.requestMessageDescriptor,
        message.metadata())

    val inputTypeUrl = message.payload().asInstanceOf[ScalaPbAny].typeUrl
    val methodInvoker = commandHandler.lookupInvoker(inputTypeUrl)

    methodInvoker
      .invoke(action, context)
      .asInstanceOf[Action.Effect[_]]
  }

  override def handleStreamedOut(
      commandName: String,
      message: MessageEnvelope[Any]): Source[Action.Effect[_], NotUsed] = {
    val componentMethod = commandHandlerLookup(commandName)
    val context =
      InvocationContext(
        message.payload().asInstanceOf[ScalaPbAny],
        componentMethod.requestMessageDescriptor,
        message.metadata())

    val typeUrl = message.payload().asInstanceOf[ScalaPbAny].typeUrl
    val methodInvoker = componentMethod.lookupInvoker(typeUrl)

    val response = methodInvoker.invoke(action, context).asInstanceOf[Flux[Action.Effect[_]]]
    Source.fromPublisher(response)
  }

  override def handleStreamedIn(commandName: String, stream: Source[MessageEnvelope[Any], NotUsed]): Action.Effect[_] =
    throw new IllegalArgumentException("Stream in calls are not supported")

  // TODO: to implement
  override def handleStreamed(
      commandName: String,
      stream: Source[MessageEnvelope[Any], NotUsed]): Source[Action.Effect[_], NotUsed] =
    throw new IllegalArgumentException("Stream in calls are not supported")
}
