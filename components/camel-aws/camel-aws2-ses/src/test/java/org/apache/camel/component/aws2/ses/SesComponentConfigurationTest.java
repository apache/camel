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
package org.apache.camel.component.aws2.ses;

import org.apache.camel.Endpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.regions.Region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SesComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        AmazonSESClientMock mock = new AmazonSESClientMock();

        context.getRegistry().bind("amazonSESClient", mock);

        Ses2Component component = context.getComponent("aws2-ses", Ses2Component.class);
        Ses2Endpoint endpoint = (Ses2Endpoint) component
                .createEndpoint("aws2-ses://from@example.com?amazonSESClient=#amazonSESClient&accessKey=xxx&secretKey=yyy");

        assertEquals("from@example.com", endpoint.getConfiguration().getFrom());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAmazonSESClient());
        assertNull(endpoint.getConfiguration().getTo());
        assertNull(endpoint.getConfiguration().getCc());
        assertNull(endpoint.getConfiguration().getBcc());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getReturnPath());
        assertNull(endpoint.getConfiguration().getReplyToAddresses());
        assertNull(endpoint.getConfiguration().getConfigurationSet());
    }

    @Test
    public void createEndpointWithOnlyAccessKeyAndSecretKey() throws Exception {
        Ses2Component component = context.getComponent("aws2-ses", Ses2Component.class);
        Ses2Endpoint endpoint
                = (Ses2Endpoint) component.createEndpoint("aws2-ses://from@example.com?accessKey=xxx&secretKey=yyy");

        assertEquals("from@example.com", endpoint.getConfiguration().getFrom());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonSESClient());
        assertNull(endpoint.getConfiguration().getTo());
        assertNull(endpoint.getConfiguration().getCc());
        assertNull(endpoint.getConfiguration().getBcc());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getReturnPath());
        assertNull(endpoint.getConfiguration().getReplyToAddresses());
        assertNull(endpoint.getConfiguration().getConfigurationSet());
    }

    @Test
    public void createEndpointWithMinimalConfigurationAndProvidedClient() throws Exception {
        AmazonSESClientMock mock = new AmazonSESClientMock();

        context.getRegistry().bind("amazonSESClient", mock);

        Ses2Component component = context.getComponent("aws2-ses", Ses2Component.class);
        Ses2Endpoint endpoint
                = (Ses2Endpoint) component.createEndpoint("aws2-ses://from@example.com?" + "amazonSESClient=#amazonSESClient");

        assertEquals("from@example.com", endpoint.getConfiguration().getFrom());
        assertNull(endpoint.getConfiguration().getAccessKey());
        assertNull(endpoint.getConfiguration().getSecretKey());
        assertSame(mock, endpoint.getConfiguration().getAmazonSESClient());
        assertNull(endpoint.getConfiguration().getTo());
        assertNull(endpoint.getConfiguration().getCc());
        assertNull(endpoint.getConfiguration().getBcc());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getReturnPath());
        assertNull(endpoint.getConfiguration().getReplyToAddresses());
        assertNull(endpoint.getConfiguration().getConfigurationSet());
    }

    @Test
    public void createEndpointWithMaximalConfiguration() throws Exception {
        AmazonSESClientMock mock = new AmazonSESClientMock();

        context.getRegistry().bind("amazonSESClient", mock);
        Ses2Component component = context.getComponent("aws2-ses", Ses2Component.class);
        Ses2Endpoint endpoint = (Ses2Endpoint) component
                .createEndpoint("aws2-ses://from@example.com?amazonSESClient=#amazonSESClient&accessKey=xxx"
                                + "&secretKey=yyy&to=to1@example.com,to2@example.com&subject=Subject"
                                + "&cc=cc1@example.com,cc2@example.com&bcc=bcc1@example.com,bcc2@example.com"
                                + "&returnPath=bounce@example.com&replyToAddresses=replyTo1@example.com,replyTo2@example.com"
                                + "&configurationSet=development-set");

        assertEquals("from@example.com", endpoint.getConfiguration().getFrom());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAmazonSESClient());
        assertTrue(endpoint.getConfiguration().getTo().contains("to1@example.com"));
        assertTrue(endpoint.getConfiguration().getTo().contains("to2@example.com"));
        assertTrue(endpoint.getConfiguration().getCc().contains("cc1@example.com"));
        assertTrue(endpoint.getConfiguration().getCc().contains("cc2@example.com"));
        assertTrue(endpoint.getConfiguration().getBcc().contains("bcc1@example.com"));
        assertTrue(endpoint.getConfiguration().getBcc().contains("bcc2@example.com"));
        assertEquals("Subject", endpoint.getConfiguration().getSubject());
        assertEquals("bounce@example.com", endpoint.getConfiguration().getReturnPath());
        assertTrue(endpoint.getConfiguration().getReplyToAddresses().contains("replyTo1@example.com"));
        assertTrue(endpoint.getConfiguration().getReplyToAddresses().contains("replyTo2@example.com"));
        assertEquals("development-set", endpoint.getConfiguration().getConfigurationSet());
    }

    @Test
    public void createEndpointWithoutSourceName() {
        Ses2Component component = context.getComponent("aws2-ses", Ses2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-ses:// ");
        });
    }

    @Test
    public void createEndpointWithoutAmazonSESClientConfiguration() {
        Ses2Component component = context.getComponent("aws2-ses", Ses2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-ses://from@example.com");
        });
    }

    @Test
    public void createEndpointWithoutAccessKeyConfiguration() {
        Ses2Component component = context.getComponent("aws2-ses", Ses2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-ses://from@example.com?secretKey=yyy");
        });
    }

    @Test
    public void createEndpointWithoutSecretKeyConfiguration() {
        Ses2Component component = context.getComponent("aws2-ses", Ses2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-ses://from@example.com?accessKey=xxx");
        });
    }

    @Test
    public void createEndpointWithComponentElements() throws Exception {
        Ses2Component component = context.getComponent("aws2-ses", Ses2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        Ses2Endpoint endpoint = (Ses2Endpoint) component.createEndpoint("aws2-ses://from@example.com");

        assertEquals("from@example.com", endpoint.getConfiguration().getFrom());
        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
    }

    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        Ses2Component component = context.getComponent("aws2-ses", Ses2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        Ses2Endpoint endpoint = (Ses2Endpoint) component
                .createEndpoint("aws2-ses://from@example.com?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");

        assertEquals("from@example.com", endpoint.getConfiguration().getFrom());
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
    }

    @Test
    public void createEndpointWithComponentEndpointElementsAndProxy() throws Exception {
        Ses2Component component = context.getComponent("aws2-ses", Ses2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        Ses2Endpoint endpoint = (Ses2Endpoint) component
                .createEndpoint(
                        "aws2-ses://label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&proxyHost=localhost&proxyPort=9000&proxyProtocol=HTTP");

        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
        assertEquals(Protocol.HTTP, endpoint.getConfiguration().getProxyProtocol());
        assertEquals("localhost", endpoint.getConfiguration().getProxyHost());
        assertEquals(Integer.valueOf(9000), endpoint.getConfiguration().getProxyPort());
    }

    @Test
    public void createEndpointWithoutSecretKeyAndAccessKeyConfiguration() throws Exception {
        AmazonSESClientMock mock = new AmazonSESClientMock();

        context.getRegistry().bind("amazonSESClient", mock);

        Ses2Component component = context.getComponent("aws2-ses", Ses2Component.class);
        Endpoint ep = component.createEndpoint("aws2-ses://from@example.com?amazonSESClient=#amazonSESClient");
        assertNotNull(ep, "Could not create the endpoint");
    }

    @Test
    public void createEndpointWithOverride() throws Exception {
        Ses2Component component = context.getComponent("aws2-ses", Ses2Component.class);
        Ses2Endpoint endpoint
                = (Ses2Endpoint) component.createEndpoint(
                        "aws2-ses://from@example.com?accessKey=xxx&secretKey=yyy&overrideEndpoint=true&uriEndpointOverride=http://localhost:9090");

        assertEquals("from@example.com", endpoint.getConfiguration().getFrom());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonSESClient());
        assertNull(endpoint.getConfiguration().getTo());
        assertNull(endpoint.getConfiguration().getCc());
        assertNull(endpoint.getConfiguration().getBcc());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getReturnPath());
        assertNull(endpoint.getConfiguration().getReplyToAddresses());
        assertTrue(endpoint.getConfiguration().isOverrideEndpoint());
        assertEquals("http://localhost:9090", endpoint.getConfiguration().getUriEndpointOverride());
    }
}
