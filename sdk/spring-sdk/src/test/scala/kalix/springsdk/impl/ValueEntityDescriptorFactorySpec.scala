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

package kalix.springsdk.impl

import com.google.protobuf.Any
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import kalix.springsdk.testmodels.valueentity.ValueEntitiesTestModels.PostWithEntityKeys
import kalix.springsdk.testmodels.valueentity.ValueEntitiesTestModels.ValueEntityWithMethodLevelAcl
import kalix.springsdk.testmodels.valueentity.ValueEntitiesTestModels.ValueEntityWithServiceLevelAcl
import org.scalatest.wordspec.AnyWordSpec

class ValueEntityDescriptorFactorySpec extends AnyWordSpec with ComponentDescriptorSuite {

  "ValueEntity descriptor factory" should {
    "generate mappings for a Value Entity with entity keys in path" in {
      assertDescriptor[PostWithEntityKeys] { desc =>
        val method = desc.commandHandlers("CreateEntity")
        assertRequestFieldJavaType(method, "json_body", JavaType.MESSAGE)
        assertRequestFieldMessageType(method, "json_body", Any.getDescriptor.getFullName)

        assertRequestFieldJavaType(method, "userId", JavaType.STRING)
        assertEntityKeyField(method, "userId")

        assertRequestFieldJavaType(method, "cartId", JavaType.STRING)
        assertEntityKeyField(method, "cartId")
      }
    }

    "generate ACL annotations at service level" in {
      assertDescriptor[ValueEntityWithServiceLevelAcl] { desc =>
        val extension = desc.serviceDescriptor.getOptions.getExtension(kalix.Annotations.service)
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }

    "generate ACL annotations at method level" in {
      assertDescriptor[ValueEntityWithMethodLevelAcl] { desc =>
        val extension = findKalixMethodOptions(desc, "CreateEntity")
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }
  }

}
