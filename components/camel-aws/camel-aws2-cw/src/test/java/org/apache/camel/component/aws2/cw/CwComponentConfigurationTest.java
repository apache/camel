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
package org.apache.camel.component.aws2.cw;

import java.time.Instant;

import org.apache.camel.BindToRegistry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CwComponentConfigurationTest extends CamelTestSupport {

    @BindToRegistry("now")
    private static final Instant NOW = Instant.now();

    @Test
    public void createEndpointWithAllOptions() throws Exception {
        CloudWatchClient cloudWatchClient = new CloudWatchClientMock();
        context.getRegistry().bind("amazonCwClient", cloudWatchClient);
        Cw2Component component = context.getComponent("aws2-cw", Cw2Component.class);
        Cw2Endpoint endpoint = (Cw2Endpoint) component
                .createEndpoint(
                        "aws2-cw://camel.apache.org/test?amazonCwClient=#amazonCwClient&name=testMetric&value=2&unit=Count&timestamp=#now");

        assertEquals("camel.apache.org/test", endpoint.getConfiguration().getNamespace());
        assertEquals("testMetric", endpoint.getConfiguration().getName());
        assertEquals(Double.valueOf(2), endpoint.getConfiguration().getValue());
        assertEquals("Count", endpoint.getConfiguration().getUnit());
        assertEquals(NOW, endpoint.getConfiguration().getTimestamp());
    }

    @Test
    public void createEndpointWithoutAccessKeyConfiguration() {
        Cw2Component component = context.getComponent("aws2-cw", Cw2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-cw://camel.apache.org/test?secretKey=yyy");
        });
    }

    @Test
    public void createEndpointWithoutSecretKeyConfiguration() {
        Cw2Component component = context.getComponent("aws2-cw", Cw2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-cw://camel.apache.org/test?accessKey=xxx");
        });
    }

    @Test
    public void createEndpointWithoutSecretKeyAndAccessKeyConfiguration() {
        Cw2Component component = context.getComponent("aws2-cw", Cw2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-cw://camel.apache.org/test");
        });
    }

    @Test
    public void createEndpointWithComponentElements() throws Exception {
        Cw2Component component = context.getComponent("aws2-cw", Cw2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        Cw2Endpoint endpoint = (Cw2Endpoint) component.createEndpoint("aws2-cw://camel.apache.org/test");

        assertEquals("camel.apache.org/test", endpoint.getConfiguration().getNamespace());
        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
    }

    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        Cw2Component component = context.getComponent("aws2-cw", Cw2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        Cw2Endpoint endpoint = (Cw2Endpoint) component
                .createEndpoint("aws2-cw://camel.apache.org/test?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");

        assertEquals("camel.apache.org/test", endpoint.getConfiguration().getNamespace());
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
    }

    @Test
    public void createEndpointWithComponentEndpointOptionsAndProxy() throws Exception {
        Cw2Component component = context.getComponent("aws2-cw", Cw2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.id());
        Cw2Endpoint endpoint = (Cw2Endpoint) component
                .createEndpoint(
                        "aws2-cw://camel.apache.org/test?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&proxyHost=localhost&proxyPort=9000");

        assertEquals("camel.apache.org/test", endpoint.getConfiguration().getNamespace());
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
        assertEquals("localhost", endpoint.getConfiguration().getProxyHost());
        assertEquals(Integer.valueOf(9000), endpoint.getConfiguration().getProxyPort());
        assertEquals(Protocol.HTTPS, endpoint.getConfiguration().getProxyProtocol());
    }

    @Test
    public void createEndpointWithEndpointOverride() throws Exception {
        Cw2Component component = context.getComponent("aws2-cw", Cw2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        Cw2Endpoint endpoint = (Cw2Endpoint) component
                .createEndpoint(
                        "aws2-cw://camel.apache.org/test?overrideEndpoint=true&uriEndpointOverride=http://localhost:9090");

        assertEquals("camel.apache.org/test", endpoint.getConfiguration().getNamespace());
        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
        assertEquals("us-west-1", endpoint.getConfiguration().getRegion());
        assertTrue(endpoint.getConfiguration().isOverrideEndpoint());
        assertEquals("http://localhost:9090", endpoint.getConfiguration().getUriEndpointOverride());
    }
}
