package org.example;

import kalix.javasdk.DeferredCall;

// This code is managed by Akka Serverless tooling.
// It will be re-generated to reflect any changes to your protobuf definitions.
// DO NOT EDIT

/**
 * Not intended for user extension, provided through generated implementation
 */
public interface Components {
  CounterServiceEntityCalls counterServiceEntity();

  interface CounterServiceEntityCalls {
    DeferredCall<org.example.valueentity.CounterApi.IncreaseValue, com.google.protobuf.Empty> increase(org.example.valueentity.CounterApi.IncreaseValue increaseValue);

    DeferredCall<org.example.valueentity.CounterApi.DecreaseValue, com.google.protobuf.Empty> decrease(org.example.valueentity.CounterApi.DecreaseValue decreaseValue);
  }
}
