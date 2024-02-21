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
package org.apache.camel.component.aws2.sts;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class STS2ComponentClientRegistryTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalSTSClientConfiguration() throws Exception {

        AmazonSTSClientMock clientMock = new AmazonSTSClientMock();
        context.getRegistry().bind("amazonStsClient", clientMock);
        STS2Component component = context.getComponent("aws2-sts", STS2Component.class);
        STS2Endpoint endpoint = (STS2Endpoint) component.createEndpoint("aws2-sts://TestDomain");

        assertNotNull(endpoint.getConfiguration().getStsClient());
    }

    @Test
    public void createEndpointWithMinimalSTSClientMisconfiguration() {

        STS2Component component = context.getComponent("aws2-sts", STS2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-sts://TestDomain");
        });
    }

    @Test
    public void createEndpointWithAutowire() throws Exception {

        AmazonSTSClientMock clientMock = new AmazonSTSClientMock();
        context.getRegistry().bind("amazonStsClient", clientMock);
        STS2Component component = context.getComponent("aws2-sts", STS2Component.class);
        STS2Endpoint endpoint = (STS2Endpoint) component.createEndpoint("aws2-sts://TestDomain?accessKey=xxx&secretKey=yyy");

        assertSame(clientMock, endpoint.getConfiguration().getStsClient());
    }
}
