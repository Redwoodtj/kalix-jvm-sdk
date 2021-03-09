/*
 * Copyright 2019 Lightbend Inc.
 */

package com.akkaserverless.javasdk.impl.entity

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.stream.scaladsl.Flow
import com.akkaserverless.javasdk.AkkaServerlessRunner.Configuration
import com.akkaserverless.javasdk.valueentity._
import com.akkaserverless.javasdk.impl._
import com.akkaserverless.javasdk.{Context, Metadata, Service, ServiceCallFactory}
import com.akkaserverless.protocol.value_entity.ValueEntityAction.Action.{Delete, Update}
import com.akkaserverless.protocol.value_entity.ValueEntityStreamIn.Message.{
  Command => InCommand,
  Empty => InEmpty,
  Init => InInit
}
import com.akkaserverless.protocol.value_entity.ValueEntityStreamOut.Message.{Failure => OutFailure, Reply => OutReply}
import com.akkaserverless.protocol.value_entity.{ValueEntity, _}
import com.google.protobuf.any.{Any => ScalaPbAny}
import com.google.protobuf.{Descriptors, Any => JavaPbAny}

import java.util.Optional
import scala.compat.java8.OptionConverters._
import scala.util.control.NonFatal

final class ValueEntityStatefulService(val factory: ValueEntityFactory,
                                       override val descriptor: Descriptors.ServiceDescriptor,
                                       val anySupport: AnySupport,
                                       override val entityType: String,
                                       override val entityOptions: Option[ValueEntityOptions])
    extends Service {

  def this(factory: ValueEntityFactory,
           descriptor: Descriptors.ServiceDescriptor,
           anySupport: AnySupport,
           entityType: String,
           entityOptions: ValueEntityOptions) =
    this(factory, descriptor, anySupport, entityType, Some(entityOptions))

  override def resolvedMethods: Option[Map[String, ResolvedServiceMethod[_, _]]] =
    factory match {
      case resolved: ResolvedEntityFactory => Some(resolved.resolvedMethods)
      case _ => None
    }

  override final val componentType = ValueEntity.name
}

final class ValueEntityImpl(_system: ActorSystem,
                            _services: Map[String, ValueEntityStatefulService],
                            rootContext: Context,
                            configuration: Configuration)
    extends ValueEntity {

  import EntityExceptions._

  private final val system = _system
  private final implicit val ec = system.dispatcher
  private final val services = _services.iterator.toMap
  private final val log = Logging(system.eventStream, this.getClass)

  /**
   * One stream will be established per active entity.
   * Once established, the first message sent will be Init, which contains the entity ID, and,
   * a state if the entity has previously persisted one. Once the Init message is sent, one to
   * many commands are sent to the entity. Each request coming in leads to a new command being sent
   * to the entity. The entity is expected to reply to each command with exactly one reply message.
   * The entity should process commands and reply to commands in the order they came
   * in. When processing a command the entity can read and persist (update or delete) the state.
   */
  override def handle(
      in: akka.stream.scaladsl.Source[ValueEntityStreamIn, akka.NotUsed]
  ): akka.stream.scaladsl.Source[ValueEntityStreamOut, akka.NotUsed] =
    in.prefixAndTail(1)
      .flatMapConcat {
        case (Seq(ValueEntityStreamIn(InInit(init), _)), source) =>
          source.via(runEntity(init))
        case _ =>
          throw ProtocolException("Expected init message for Value entity")
      }
      .recover {
        case error =>
          log.error(error, failureMessage(error))
          ValueEntityStreamOut(OutFailure(failure(error)))
      }

  private def runEntity(init: ValueEntityInit): Flow[ValueEntityStreamIn, ValueEntityStreamOut, NotUsed] = {
    val service =
      services.getOrElse(init.serviceName, throw ProtocolException(init, s"Service not found: ${init.serviceName}"))
    val handler = service.factory.create(new EntityContextImpl(init.entityId))
    val thisEntityId = init.entityId

    val initState = init.state match {
      case Some(ValueEntityInitState(state, _)) => state
      case _ => None // should not happen!!!
    }

    Flow[ValueEntityStreamIn]
      .map(_.message)
      .scan[(Option[ScalaPbAny], Option[ValueEntityStreamOut.Message])]((initState, None)) {
        case (_, InCommand(command)) if thisEntityId != command.entityId =>
          throw ProtocolException(command, "Receiving Value entity is not the intended recipient of command")

        case (_, InCommand(command)) if command.payload.isEmpty =>
          throw ProtocolException(command, "No command payload for Value entity")

        case ((state, _), InCommand(command)) =>
          val cmd = ScalaPbAny.toJavaProto(command.payload.get)
          val metadata = new MetadataImpl(command.metadata.map(_.entries.toVector).getOrElse(Nil))
          val context = new CommandContextImpl(
            thisEntityId,
            command.name,
            command.id,
            metadata,
            state,
            service.anySupport,
            log
          )
          val reply = try {
            handler.handleCommand(cmd, context)
          } catch {
            case FailInvoked => Option.empty[JavaPbAny].asJava
            case e: EntityException => throw e
            case NonFatal(error) =>
              throw EntityException(command, s"Value entity unexpected failure: ${error.getMessage}")
          } finally {
            context.deactivate() // Very important!
          }

          val clientAction = context.createClientAction(reply, false, false)
          if (!context.hasError) {
            val nextState = context.currentState()
            (nextState,
             Some(
               OutReply(
                 ValueEntityReply(
                   command.id,
                   clientAction,
                   context.sideEffects,
                   context.action
                 )
               )
             ))
          } else {
            // rollback the state if something went wrong by using the old state
            (state,
             Some(
               OutReply(
                 ValueEntityReply(
                   commandId = command.id,
                   clientAction = clientAction
                 )
               )
             ))
          }

        case (_, InInit(_)) =>
          throw ProtocolException(init, "Value entity already inited")

        case (_, InEmpty) =>
          throw ProtocolException(init, "Value entity received empty/unknown message")
      }
      .collect {
        case (_, Some(message)) => ValueEntityStreamOut(message)
      }
  }

  trait AbstractContext extends ValueEntityContext {
    override def serviceCallFactory(): ServiceCallFactory = rootContext.serviceCallFactory()
  }

  private final class CommandContextImpl(override val entityId: String,
                                         override val commandName: String,
                                         override val commandId: Long,
                                         override val metadata: Metadata,
                                         val state: Option[ScalaPbAny],
                                         val anySupport: AnySupport,
                                         val log: LoggingAdapter)
      extends CommandContext[JavaPbAny]
      with AbstractContext
      with AbstractClientActionContext
      with AbstractEffectContext
      with ActivatableContext {

    final var action: Option[ValueEntityAction] = None
    private var _state: Option[ScalaPbAny] = state

    override def getState(): Optional[JavaPbAny] = {
      checkActive()
      _state.map(ScalaPbAny.toJavaProto(_)).asJava
    }

    override def updateState(state: JavaPbAny): Unit = {
      checkActive()
      if (state == null)
        throw EntityException("Value entity cannot update a 'null' state")

      val encoded = anySupport.encodeScala(state)
      _state = Some(encoded)
      action = Some(ValueEntityAction(Update(ValueEntityUpdate(_state))))
    }

    override def deleteState(): Unit = {
      checkActive()

      _state = None
      action = Some(ValueEntityAction(Delete(ValueEntityDelete())))
    }

    override protected def logError(message: String): Unit =
      log.error("Fail invoked for command [{}] for Value entity [{}]: {}", commandName, entityId, message)

    def currentState(): Option[ScalaPbAny] =
      _state

  }

  private final class EntityContextImpl(override final val entityId: String)
      extends ValueEntityContext
      with AbstractContext

}