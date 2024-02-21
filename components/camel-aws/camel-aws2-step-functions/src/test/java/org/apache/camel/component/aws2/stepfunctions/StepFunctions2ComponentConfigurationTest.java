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
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.regions.Region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StepFunctions2ComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithComponentElements() throws Exception {
        StepFunctions2Component component = context.getComponent("aws2-step-functions", StepFunctions2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        StepFunctions2Endpoint endpoint = (StepFunctions2Endpoint) component.createEndpoint("aws2-step-functions://label");

        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
    }

    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        StepFunctions2Component component = context.getComponent("aws2-step-functions", StepFunctions2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        StepFunctions2Endpoint endpoint
                = (StepFunctions2Endpoint) component
                        .createEndpoint("aws2-step-functions://label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");

        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
    }

    @Test
    public void createEndpointWithComponentEndpointElementsAndProxy() throws Exception {
        StepFunctions2Component component = context.getComponent("aws2-step-functions", StepFunctions2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        StepFunctions2Endpoint endpoint = (StepFunctions2Endpoint) component
                .createEndpoint(
                        "aws2-step-functions://label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&proxyHost=localhost&proxyPort=9000&proxyProtocol=HTTP");

        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
        assertEquals(Protocol.HTTP, endpoint.getConfiguration().getProxyProtocol());
        assertEquals("localhost", endpoint.getConfiguration().getProxyHost());
        assertEquals(Integer.valueOf(9000), endpoint.getConfiguration().getProxyPort());
    }

    @Test
    public void createEndpointWithOverrideEndpoint() throws Exception {
        StepFunctions2Component component = context.getComponent("aws2-step-functions", StepFunctions2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        StepFunctions2Endpoint endpoint
                = (StepFunctions2Endpoint) component.createEndpoint(
                        "aws2-step-functions://label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&overrideEndpoint=true&uriEndpointOverride=http://localhost:9090");

        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
        assertTrue(endpoint.getConfiguration().isOverrideEndpoint());
        assertEquals("http://localhost:9090", endpoint.getConfiguration().getUriEndpointOverride());
    }
}
