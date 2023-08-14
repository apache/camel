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
package org.apache.camel.component.aws2.sns;

import org.apache.camel.Endpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.regions.Region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnsComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        AmazonSNSClientMock mock = new AmazonSNSClientMock();

        context.getRegistry().bind("amazonSNSClient", mock);

        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        Sns2Endpoint endpoint = (Sns2Endpoint) component
                .createEndpoint("aws2-sns://MyTopic?amazonSNSClient=#amazonSNSClient&accessKey=xxx&secretKey=yyy");

        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAmazonSNSClient());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getPolicy());
    }

    @Test
    public void createEndpointWithOnlyAccessKeyAndSecretKey() throws Exception {
        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        Sns2Endpoint endpoint = (Sns2Endpoint) component.createEndpoint("aws2-sns://MyTopic?accessKey=xxx&secretKey=yyy");

        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonSNSClient());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getPolicy());
    }

    @Test
    public void createEndpointWithMinimalArnConfiguration() throws Exception {
        AmazonSNSClientMock mock = new AmazonSNSClientMock();

        context.getRegistry().bind("amazonSNSClient", mock);

        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        Sns2Endpoint endpoint = (Sns2Endpoint) component
                .createEndpoint(
                        "aws2-sns://arn:aws:sns:us-east-1:account:MyTopic?amazonSNSClient=#amazonSNSClient&accessKey=xxx&secretKey=yyy");

        assertNull(endpoint.getConfiguration().getTopicName());
        assertEquals("arn:aws:sns:us-east-1:account:MyTopic", endpoint.getConfiguration().getTopicArn());
    }

    @Test
    public void createEndpointWithMinimalConfigurationAndProvidedClient() throws Exception {
        AmazonSNSClientMock mock = new AmazonSNSClientMock();

        context.getRegistry().bind("amazonSNSClient", mock);

        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        Sns2Endpoint endpoint = (Sns2Endpoint) component.createEndpoint("aws2-sns://MyTopic?amazonSNSClient=#amazonSNSClient");

        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertNull(endpoint.getConfiguration().getAccessKey());
        assertNull(endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getPolicy());
        endpoint.start();

        assertEquals("arn:aws:sns:us-east-1:541925086079:MyTopic", endpoint.getConfiguration().getTopicArn());

        endpoint.stop();
    }

    @Test
    public void createEndpointWithMaximalConfiguration() throws Exception {

        AmazonSNSClientMock mock = new AmazonSNSClientMock();

        context.getRegistry().bind("amazonSNSClient", mock);

        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        Sns2Endpoint endpoint = (Sns2Endpoint) component
                .createEndpoint("aws2-sns://MyTopic?amazonSNSClient=#amazonSNSClient&accessKey=xxx&secretKey=yyy"
                                + "&policy=file:src/test/resources/org/apache/camel/component/aws2/sns/policy.txt&subject=The%20subject%20message");

        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNotNull(endpoint.getConfiguration().getAmazonSNSClient());
        assertEquals("The subject message", endpoint.getConfiguration().getSubject());
        assertEquals(
                "file:src/test/resources/org/apache/camel/component/aws2/sns/policy.txt",
                endpoint.getConfiguration().getPolicy());
        endpoint.start();
    }

    @Test
    public void createEndpointWithSQSSubscription() throws Exception {
        AmazonSNSClientMock mock = new AmazonSNSClientMock();

        context.getRegistry().bind("amazonSNSClient", mock);
        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        Sns2Endpoint endpoint = (Sns2Endpoint) component.createEndpoint("aws2-sns://MyTopic?amazonSNSClient=#amazonSNSClient&"
                                                                        + "accessKey=xxx&secretKey=yyy&queueArn=arn:aws:sqs:us-east-1:541925086079:MyQueue&subscribeSNStoSQS=true");

        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("arn:aws:sqs:us-east-1:541925086079:MyQueue", endpoint.getConfiguration().getQueueArn());
        assertNotNull(endpoint.getConfiguration().getAmazonSNSClient());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getPolicy());
    }

    @Test
    public void createEndpointWithoutAccessKeyConfiguration() {
        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-sns://MyTopic?secretKey=yyy");
        });
    }

    @Test
    public void createEndpointWithoutSecretKeyConfiguration() {
        Sns2Component component = new Sns2Component(context);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-sns://MyTopic?accessKey=xxx");
        });
    }

    @Test
    public void createEndpointWithComponentElements() throws Exception {
        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        Sns2Endpoint endpoint = (Sns2Endpoint) component.createEndpoint("aws2-sns://MyTopic");

        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
    }

    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        Sns2Endpoint endpoint = (Sns2Endpoint) component
                .createEndpoint("aws2-sns://MyTopic?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");

        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
    }

    @Test
    public void createEndpointWithoutAutocreation() throws Exception {
        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        Sns2Endpoint endpoint = (Sns2Endpoint) component
                .createEndpoint("aws2-sns://MyTopic?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&autoCreateTopic=false");

        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
        assertEquals(false, endpoint.getConfiguration().isAutoCreateTopic());
    }

    @Test
    public void createEndpointWithComponentEndpointElementsAndProxy() throws Exception {
        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        Sns2Endpoint endpoint = (Sns2Endpoint) component
                .createEndpoint(
                        "aws2-sns://label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&proxyHost=localhost&proxyPort=9000&proxyProtocol=HTTP");

        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
        assertEquals(Protocol.HTTP, endpoint.getConfiguration().getProxyProtocol());
        assertEquals("localhost", endpoint.getConfiguration().getProxyHost());
        assertEquals(Integer.valueOf(9000), endpoint.getConfiguration().getProxyPort());
    }

    @Test
    public void createEndpointWithoutSecretKeyAndAccessKeyConfiguration() throws Exception {
        AmazonSNSClientMock mock = new AmazonSNSClientMock();

        context.getRegistry().bind("amazonSNSClient", mock);

        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        Endpoint ep = component.createEndpoint("aws2-sns://MyTopic?amazonSNSClient=#amazonSNSClient");
        assertNotNull(ep, "Could not create the endpoint");
    }

    @Test
    public void createEndpointWithArnConfiguration() throws Exception {
        AmazonSNSClientMock mock = new AmazonSNSClientMock();

        context.getRegistry().bind("amazonSNSClient", mock);

        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        Sns2Endpoint endpoint = (Sns2Endpoint) component
                .createEndpoint(
                        "aws2-sns://arn:aws:sns:eu-west-1:123456789:somewhere-over-the-rainbow?amazonSNSClient=#amazonSNSClient&accessKey=xxx&secretKey=yyy");

        assertEquals("arn:aws:sns:eu-west-1:123456789:somewhere-over-the-rainbow", endpoint.getConfiguration().getTopicArn());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAmazonSNSClient());
        assertNull(endpoint.getConfiguration().getTopicName());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getPolicy());
        assertFalse(endpoint.getConfiguration().isFifoTopic());
    }

    @Test
    public void createEndpointWithOverride() throws Exception {
        Sns2Component component = context.getComponent("aws2-sns", Sns2Component.class);
        Sns2Endpoint endpoint = (Sns2Endpoint) component.createEndpoint(
                "aws2-sns://MyTopic?accessKey=xxx&secretKey=yyy&overrideEndpoint=true&uriEndpointOverride=http://localhost:9090");

        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonSNSClient());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getPolicy());
        assertTrue(endpoint.getConfiguration().isOverrideEndpoint());
        assertEquals("http://localhost:9090", endpoint.getConfiguration().getUriEndpointOverride());
    }
}
