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
package org.apache.camel.component.aws.sns;

import com.amazonaws.Protocol;
import com.amazonaws.regions.Regions;
import org.apache.camel.component.aws.sqs.AmazonSQSClientMock;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SnsComponentConfigurationTest extends CamelTestSupport {
    
    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        AmazonSNSClientMock mock = new AmazonSNSClientMock();
        
        context.getRegistry().bind("amazonSNSClient", mock);

        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://MyTopic?amazonSNSClient=#amazonSNSClient&accessKey=xxx&secretKey=yyy");
        
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
        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://MyTopic?accessKey=xxx&secretKey=yyy");

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

        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://arn:aws:sns:us-east-1:account:MyTopic?amazonSNSClient=#amazonSNSClient&accessKey=xxx&secretKey=yyy");

        assertNull(endpoint.getConfiguration().getTopicName());
        assertEquals("arn:aws:sns:us-east-1:account:MyTopic", endpoint.getConfiguration().getTopicArn());
    }

    @Test
    public void createEndpointWithMinimalConfigurationAndProvidedClient() throws Exception {
        AmazonSNSClientMock mock = new AmazonSNSClientMock();
        
        context.getRegistry().bind("amazonSNSClient", mock);
        
        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://MyTopic?amazonSNSClient=#amazonSNSClient");
        
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
        
        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://MyTopic?amazonSNSClient=#amazonSNSClient&accessKey=xxx&secretKey=yyy"
                + "&policy=%7B%22Version%22%3A%222008-10-17%22,%22Statement%22%3A%5B%7B%22Sid%22%3A%221%22,%22Effect%22%3A%22Allow%22,%22Principal%22%3A%7B%22AWS%22%3A%5B%22*%22%5D%7D,"
                + "%22Action%22%3A%5B%22sns%3ASubscribe%22%5D%7D%5D%7D&subject=The+subject+message");
        
        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNotNull(endpoint.getConfiguration().getAmazonSNSClient());
        assertEquals("The subject message", endpoint.getConfiguration().getSubject());
        assertEquals(
                "{\"Version\":\"2008-10-17\",\"Statement\":[{\"Sid\":\"1\",\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"sns:Subscribe\"]}]}",
                endpoint.getConfiguration().getPolicy());
    }
    
    @Test
    public void createEndpointWithSQSSubscription() throws Exception {
        AmazonSNSClientMock mock = new AmazonSNSClientMock();
        AmazonSQSClientMock mockSQS = new AmazonSQSClientMock();
        
        context.getRegistry().bind("amazonSNSClient", mock);
        context.getRegistry().bind("amazonSQSClient", mockSQS);
        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://MyTopic?amazonSNSClient=#amazonSNSClient&" 
        + "accessKey=xxx&secretKey=yyy&amazonSQSClient=#amazonSQSClient&queueUrl=arn:aws:sqs:us-east-1:541925086079:MyQueue&subscribeSNStoSQS=true");
        
        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("arn:aws:sqs:us-east-1:541925086079:MyQueue", endpoint.getConfiguration().getQueueUrl());
        assertNotNull(endpoint.getConfiguration().getAmazonSNSClient());
        assertNotNull(endpoint.getConfiguration().getAmazonSQSClient());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getPolicy());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithSQSSubscriptionIllegalArgument() throws Exception {
        AmazonSNSClientMock mock = new AmazonSNSClientMock();
        AmazonSQSClientMock mockSQS = new AmazonSQSClientMock();
        
        context.getRegistry().bind("amazonSNSClient", mock);
        context.getRegistry().bind("amazonSQSClient", mockSQS);
        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://MyTopic?amazonSNSClient=#amazonSNSClient&accessKey=xxx"
        + "&secretKey=yyy&amazonSQSClient=#amazonSQSClient&subscribeSNStoSQS=true");
        
        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getQueueUrl());
        assertNotNull(endpoint.getConfiguration().getAmazonSNSClient());
        assertNotNull(endpoint.getConfiguration().getAmazonSQSClient());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getPolicy());
        
        endpoint.start();
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAccessKeyConfiguration() throws Exception {
        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        component.createEndpoint("aws-sns://MyTopic?secretKey=yyy");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutSecretKeyConfiguration() throws Exception {
        SnsComponent component = new SnsComponent(context);
        component.createEndpoint("aws-sns://MyTopic?accessKey=xxx");
    }
    
    @Test
    public void createEndpointWithComponentElements() throws Exception {
        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        SnsEndpoint endpoint = (SnsEndpoint)component.createEndpoint("aws-sns://MyTopic");
        
        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
    }
    
    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Regions.US_WEST_1.toString());
        SnsEndpoint endpoint = (SnsEndpoint)component.createEndpoint("aws-sns://MyTopic?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");
        
        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
    }
    
    @Test
    public void createEndpointWithoutAutocreation() throws Exception {
        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Regions.US_WEST_1.toString());
        SnsEndpoint endpoint = (SnsEndpoint)component.createEndpoint("aws-sns://MyTopic?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&autoCreateTopic=false");
        
        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
        assertEquals(false, endpoint.getConfiguration().isAutoCreateTopic());
    }
    
    @Test
    public void createEndpointWithComponentEndpointElementsAndProxy() throws Exception {
        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Regions.US_WEST_1.toString());
        SnsEndpoint endpoint = (SnsEndpoint)component.createEndpoint("aws-sns://label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&proxyHost=localhost&proxyPort=9000&proxyProtocol=HTTP");
        
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

        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        component.createEndpoint("aws-sns://MyTopic?amazonSNSClient=#amazonSNSClient");
    }
}
