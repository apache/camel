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
package org.apache.camel.component.aws2.stepfunctions;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StepFunctions2ComponentClientRegistryTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalSfnClientConfiguration() throws Exception {

        AmazonStepFunctionsClientMock clientMock = new AmazonStepFunctionsClientMock();
        context.getRegistry().bind("amazonSfnClient", clientMock);
        StepFunctions2Component component = context.getComponent("aws2-step-functions", StepFunctions2Component.class);
        StepFunctions2Endpoint endpoint = (StepFunctions2Endpoint) component.createEndpoint("aws2-step-functions://TestDomain");

        assertNotNull(endpoint.getConfiguration().getAwsSfnClient());
    }

    @Test
    public void createEndpointWithMinimalSfnClientMisconfiguration() {

        StepFunctions2Component component = context.getComponent("aws2-step-functions", StepFunctions2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-step-functions://TestDomain");
        });
    }

    @Test
    public void createEndpointWithAutowired() throws Exception {

        AmazonStepFunctionsClientMock clientMock = new AmazonStepFunctionsClientMock();
        context.getRegistry().bind("awsSfnClient", clientMock);
        StepFunctions2Component component = context.getComponent("aws2-step-functions", StepFunctions2Component.class);
        StepFunctions2Endpoint endpoint = (StepFunctions2Endpoint) component
                .createEndpoint("aws2-step-functions://TestDomain?accessKey=xxx&secretKey=yyy");

        assertSame(clientMock, endpoint.getConfiguration().getAwsSfnClient());
    }
}
