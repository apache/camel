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
package org.apache.camel.component.aws2.lambda;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LambdaComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        LambdaClient awsLambdaClient = new AmazonLambdaClientMock();
        context.getRegistry().bind("awsLambdaClient", awsLambdaClient);
        Lambda2Component component = context.getComponent("aws2-lambda", Lambda2Component.class);
        Lambda2Endpoint endpoint = (Lambda2Endpoint)component
            .createEndpoint("aws2-lambda://myFunction?operation=getFunction&awsLambdaClient=#awsLambdaClient&accessKey=xxx&secretKey=yyy");

        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAwsLambdaClient());
    }

    @Test
    public void createEndpointWithoutOperation() throws Exception {
        Lambda2Component component = context.getComponent("aws2-lambda", Lambda2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-lambda://myFunction");
        });
    }

    @Test
    public void createEndpointWithoutAmazonLambdaClientConfiguration() throws Exception {
        Lambda2Component component = context.getComponent("aws2-lambda", Lambda2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-lambda://myFunction?operation=getFunction");
        });
    }

    @Test
    public void createEndpointWithoutAccessKeyConfiguration() throws Exception {
        Lambda2Component component = context.getComponent("aws2-lambda", Lambda2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-lambda://myFunction?operation=getFunction&secretKey=yyy");
        });
    }

    @Test
    public void createEndpointWithoutSecretKeyConfiguration() throws Exception {
        Lambda2Component component = context.getComponent("aws2-lambda", Lambda2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-lambda://myFunction?operation=getFunction&accessKey=xxx");
        });
    }

    @Test
    public void createEndpointWithoutSecretKeyAndAccessKeyConfiguration() throws Exception {
        LambdaClient awsLambdaClient = new AmazonLambdaClientMock();
        context.getRegistry().bind("awsLambdaClient", awsLambdaClient);
        Lambda2Component component = context.getComponent("aws2-lambda", Lambda2Component.class);
        component.createEndpoint("aws2-lambda://myFunction?operation=getFunction&awsLambdaClient=#awsLambdaClient");
    }

    @Test
    public void createEndpointWithComponentElements() throws Exception {
        Lambda2Component component = context.getComponent("aws2-lambda", Lambda2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        Lambda2Endpoint endpoint = (Lambda2Endpoint)component.createEndpoint("aws2-lambda://myFunction");

        assertEquals("myFunction", endpoint.getFunction());
        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
    }

    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        Lambda2Component component = context.getComponent("aws2-lambda", Lambda2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        Lambda2Endpoint endpoint = (Lambda2Endpoint)component.createEndpoint("aws2-lambda://myFunction?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");

        assertEquals("myFunction", endpoint.getFunction());
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
    }

    @Test
    public void createEndpointWithComponentEndpointElementsAndProxy() throws Exception {
        Lambda2Component component = context.getComponent("aws2-lambda", Lambda2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        Lambda2Endpoint endpoint = (Lambda2Endpoint)component
            .createEndpoint("aws2-lambda://label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&proxyHost=localhost&proxyPort=9000&proxyProtocol=HTTP");

        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
        assertEquals(Protocol.HTTP, endpoint.getConfiguration().getProxyProtocol());
        assertEquals("localhost", endpoint.getConfiguration().getProxyHost());
        assertEquals(Integer.valueOf(9000), endpoint.getConfiguration().getProxyPort());
    }
}
