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
package org.apache.camel.component.aws.iam;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IAMComponentClientRegistryTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalECSClientConfiguration() throws Exception {

        AmazonIAMClientMock clientMock = new AmazonIAMClientMock();
        context.getRegistry().bind("amazonIamClient", clientMock);
        IAMComponent component = context.getComponent("aws-iam", IAMComponent.class);
        IAMEndpoint endpoint = (IAMEndpoint) component.createEndpoint("aws-iam://TestDomain");

        assertNotNull(endpoint.getConfiguration().getIamClient());
    }

    @Test
    public void createEndpointWithMinimalECSClientMisconfiguration() throws Exception {

        IAMComponent component = context.getComponent("aws-iam", IAMComponent.class);
        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("aws-iam://TestDomain"));
    }

    @Test
    public void createEndpointWithAutoDiscoverClientFalse() throws Exception {

        AmazonIAMClientMock clientMock = new AmazonIAMClientMock();
        context.getRegistry().bind("amazonIamClient", clientMock);
        IAMComponent component = context.getComponent("aws-iam", IAMComponent.class);
        IAMEndpoint endpoint = (IAMEndpoint) component
                .createEndpoint("aws-iam://TestDomain?accessKey=xxx&secretKey=yyy&autoDiscoverClient=false");

        assertNotSame(clientMock, endpoint.getConfiguration().getIamClient());
    }

    @Test
    public void createEndpointWithAutoDiscoverClientTrue() throws Exception {

        AmazonIAMClientMock clientMock = new AmazonIAMClientMock();
        context.getRegistry().bind("amazonIamClient", clientMock);
        IAMComponent component = context.getComponent("aws-iam", IAMComponent.class);
        IAMEndpoint endpoint = (IAMEndpoint) component.createEndpoint("aws-iam://TestDomain?accessKey=xxx&secretKey=yyy");

        assertSame(clientMock, endpoint.getConfiguration().getIamClient());
    }
}
