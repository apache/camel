/**
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
package org.apache.camel.component.aws.sqs;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SqsComponentConfigurationTest extends CamelTestSupport {
    
    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        SqsComponent component = new SqsComponent(context);
        SqsEndpoint endpoint = (SqsEndpoint) component.createEndpoint("aws-sqs://MyQueue?accessKey=xxx&secretKey=yyy");
        
        assertEquals("MyQueue", endpoint.getConfiguration().getQueueName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonSQSClient());
        assertNull(endpoint.getConfiguration().getAttributeNames());
        assertNull(endpoint.getConfiguration().getDefaultVisibilityTimeout());
        assertNull(endpoint.getConfiguration().getVisibilityTimeout());
        assertNull(endpoint.getConfiguration().getAmazonSQSEndpoint());
        assertNull(endpoint.getConfiguration().getMaximumMessageSize());
        assertNull(endpoint.getConfiguration().getMessageRetentionPeriod());
        assertNull(endpoint.getConfiguration().getPolicy());
    }
    
    @Test
    public void createEndpointWithMinimalConfigurationAndProvidedClient() throws Exception {
        AmazonSQSClientMock mock = new AmazonSQSClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("amazonSQSClient", mock);
        
        SqsComponent component = new SqsComponent(context);
        SqsEndpoint endpoint = (SqsEndpoint) component.createEndpoint("aws-sqs://MyQueue?amazonSQSClient=#amazonSQSClient");
        
        assertEquals("MyQueue", endpoint.getConfiguration().getQueueName());
        assertNull(endpoint.getConfiguration().getAccessKey());
        assertNull(endpoint.getConfiguration().getSecretKey());
        assertSame(mock, endpoint.getConfiguration().getAmazonSQSClient());
        assertNull(endpoint.getConfiguration().getAttributeNames());
        assertNull(endpoint.getConfiguration().getDefaultVisibilityTimeout());
        assertNull(endpoint.getConfiguration().getVisibilityTimeout());
        assertNull(endpoint.getConfiguration().getAmazonSQSEndpoint());
        assertNull(endpoint.getConfiguration().getMaximumMessageSize());
        assertNull(endpoint.getConfiguration().getMessageRetentionPeriod());
        assertNull(endpoint.getConfiguration().getPolicy());
    }
    
    @Test
    public void createEndpointWithMaximalConfiguration() throws Exception {
        List<String> attributeNames = new ArrayList<String>();
        attributeNames.add("color");
        attributeNames.add("size");
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("attributeNames", attributeNames);
        
        SqsComponent component = new SqsComponent(context);
        SqsEndpoint endpoint = (SqsEndpoint) component.createEndpoint("aws-sqs://MyQueue?amazonSQSEndpoint=sns.eu-west-1.amazonaws.com&accessKey=xxx&secretKey=yyy&attributeNames=#attributeNames"
                + "&DefaultVisibilityTimeout=1000&visibilityTimeout=2000&maximumMessageSize=65536&messageRetentionPeriod=1209600&policy="
                + "%7B%22Version%22%3A%222008-10-17%22%2C%22Id%22%3A%22%2F195004372649%2FMyQueue%2FSQSDefaultPolicy%22%2C%22Statement%22%3A%5B%7B%22Sid%22%3A%22Queue1ReceiveMessage%22%2C%22"
                + "Effect%22%3A%22Allow%22%2C%22Principal%22%3A%7B%22AWS%22%3A%22*%22%7D%2C%22Action%22%3A%22SQS%3AReceiveMessage%22%2C%22Resource%22%3A%22%2F195004372649%2FMyQueue%22%7D%5D%7D");
        
        assertEquals("MyQueue", endpoint.getConfiguration().getQueueName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonSQSClient());
        assertSame(attributeNames, endpoint.getConfiguration().getAttributeNames());
        assertEquals(new Integer(1000), endpoint.getConfiguration().getDefaultVisibilityTimeout());
        assertEquals(new Integer(2000), endpoint.getConfiguration().getVisibilityTimeout());
        assertEquals("sns.eu-west-1.amazonaws.com", endpoint.getConfiguration().getAmazonSQSEndpoint());
        assertEquals(new Integer(65536), endpoint.getConfiguration().getMaximumMessageSize());
        assertEquals(new Integer(1209600), endpoint.getConfiguration().getMessageRetentionPeriod());
        assertEquals("{\"Version\":\"2008-10-17\",\"Id\":\"/195004372649/MyQueue/SQSDefaultPolicy\",\"Statement\":[{\"Sid\":\"Queue1ReceiveMessage\",\"Effect\":\"Allow\",\"Principal\":"
                + "{\"AWS\":\"*\"},\"Action\":\"SQS:ReceiveMessage\",\"Resource\":\"/195004372649/MyQueue\"}]}",
                endpoint.getConfiguration().getPolicy());
    }
    
    @Test
    public void createEndpointWithPollConsumerConfiguration() throws Exception {
        SqsComponent component = new SqsComponent(context);
        SqsEndpoint endpoint = (SqsEndpoint) component.createEndpoint("aws-sqs://MyQueue?accessKey=xxx&secretKey=yyy&initialDelay=300&delay=400&maxMessagesPerPoll=50");
        SqsConsumer consumer = (SqsConsumer) endpoint.createConsumer(null);
        
        assertEquals(300, consumer.getInitialDelay());
        assertEquals(400, consumer.getDelay());
        assertEquals(50, consumer.getMaxMessagesPerPoll());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAccessKeyConfiguration() throws Exception {
        SqsComponent component = new SqsComponent(context);
        component.createEndpoint("aws-sqs://MyQueue?secretKey=yyy");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutSecretKeyConfiguration() throws Exception {
        SqsComponent component = new SqsComponent(context);
        component.createEndpoint("aws-sqs://MyQueue?accessKey=xxx");
    }
}