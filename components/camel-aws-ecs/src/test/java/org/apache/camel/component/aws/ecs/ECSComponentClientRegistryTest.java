/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.aws.ecs;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ECSComponentClientRegistryTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalECSClientConfiguration() throws Exception {

        AmazonECSClientMock clientMock = new AmazonECSClientMock();
        context.getRegistry().bind("amazonEcsClient", clientMock);
        ECSComponent component = context.getComponent("aws-ecs", ECSComponent.class);
        ECSEndpoint endpoint = (ECSEndpoint) component.createEndpoint("aws-ecs://TestDomain");

        assertNotNull(endpoint.getConfiguration().getEcsClient());
    }

    @Test
    public void createEndpointWithMinimalECSClientMisconfiguration() throws Exception {

        ECSComponent component = context.getComponent("aws-ecs", ECSComponent.class);
        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("aws-ecs://TestDomain"));
    }

    @Test
    public void createEndpointWithAutoDiscoverClientFalse() throws Exception {

        AmazonECSClientMock clientMock = new AmazonECSClientMock();
        context.getRegistry().bind("amazonEcsClient", clientMock);
        ECSComponent component = context.getComponent("aws-ecs", ECSComponent.class);
        ECSEndpoint endpoint = (ECSEndpoint) component
                .createEndpoint("aws-ecs://TestDomain?accessKey=xxx&secretKey=yyy&autoDiscoverClient=false");

        assertNotSame(clientMock, endpoint.getConfiguration().getEcsClient());
    }

    @Test
    public void createEndpointWithAutoDiscoverClientTrue() throws Exception {

        AmazonECSClientMock clientMock = new AmazonECSClientMock();
        context.getRegistry().bind("amazonEcsClient", clientMock);
        ECSComponent component = context.getComponent("aws-ecs", ECSComponent.class);
        ECSEndpoint endpoint = (ECSEndpoint) component.createEndpoint("aws-ecs://TestDomain?accessKey=xxx&secretKey=yyy");

        assertSame(clientMock, endpoint.getConfiguration().getEcsClient());
    }
}
