
package com.example.replicated.multimap.domain

import com.example.replicated.multimap
import com.example.replicated.multimap.domain.key.MultiMapKeyProto
import com.example.replicated.multimap.domain.value.MultiMapValueProto
import com.google.protobuf.Descriptors
import com.google.protobuf.empty.EmptyProto
import kalix.scalasdk.replicatedentity.ReplicatedEntityContext
import kalix.scalasdk.replicatedentity.ReplicatedEntityOptions
import kalix.scalasdk.replicatedentity.ReplicatedEntityProvider
import kalix.scalasdk.replicatedentity.ReplicatedMultiMap

import scala.collection.immutable.Seq

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

/**
 * A replicated entity provider that defines how to register and create the entity for
 * the Protobuf service `MultiMapService`.
 *
 * Should be used with the `register` method in [[kalix.scalasdk.AkkaServerless]].
 */
object SomeMultiMapProvider {
  def apply(entityFactory: ReplicatedEntityContext => SomeMultiMap): SomeMultiMapProvider =
    new SomeMultiMapProvider(entityFactory, ReplicatedEntityOptions.defaults)

  def apply(entityFactory: ReplicatedEntityContext => SomeMultiMap, options: ReplicatedEntityOptions): SomeMultiMapProvider =
    new SomeMultiMapProvider(entityFactory, options)
}


class SomeMultiMapProvider private (
    entityFactory: ReplicatedEntityContext => SomeMultiMap,
    override val options: ReplicatedEntityOptions)
    extends ReplicatedEntityProvider[ReplicatedMultiMap[com.example.replicated.multimap.domain.key.SomeKey, com.example.replicated.multimap.domain.value.SomeValue], SomeMultiMap] {

  override def entityType: String = "some-multi-map"

  override def newRouter(context: ReplicatedEntityContext): SomeMultiMapRouter =
    new SomeMultiMapRouter(entityFactory(context))

  override def serviceDescriptor: Descriptors.ServiceDescriptor =
    multimap.MultiMapApiProto.javaDescriptor.findServiceByName("MultiMapService")

  override def additionalDescriptors: Seq[Descriptors.FileDescriptor] =
    EmptyProto.javaDescriptor :: MultiMapKeyProto.javaDescriptor :: MultiMapValueProto.javaDescriptor :: multimap.MultiMapApiProto.javaDescriptor :: Nil
}
