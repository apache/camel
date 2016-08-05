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
package org.apache.camel.component.aws.sns;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SnsComponentConfigurationTest extends CamelTestSupport {
    
    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        SnsComponent component = new SnsComponent(context);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://MyTopic?accessKey=xxx&secretKey=yyy");
        
        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonSNSClient());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getAmazonSNSEndpoint());
        assertNull(endpoint.getConfiguration().getPolicy());
    }

    public void createEndpointWithMinimalArnConfiguration() throws Exception {
        SnsComponent component = new SnsComponent(context);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://arn:aws:sns:region:account:MyTopic?accessKey=xxx&secretKey=yyy");

        assertNull(endpoint.getConfiguration().getTopicName());
        assertEquals("arn:aws:sns:region:account:MyTopic", endpoint.getConfiguration().getTopicArn());
    }

    @Test
    public void createEndpointWithMinimalConfigurationAndProvidedClient() throws Exception {
        AmazonSNSClientMock mock = new AmazonSNSClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("amazonSNSClient", mock);
        
        SnsComponent component = new SnsComponent(context);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://MyTopic?amazonSNSClient=#amazonSNSClient&amazonSNSEndpoint=sns.ap-southeast-2.amazonaws.com");
        
        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertNull(endpoint.getConfiguration().getAccessKey());
        assertNull(endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNotNull(endpoint.getConfiguration().getAmazonSNSEndpoint());
        assertNull(endpoint.getConfiguration().getPolicy());
        endpoint.start();
        
        assertEquals("arn:aws:sns:us-east-1:541925086079:MyTopic", endpoint.getConfiguration().getTopicArn());
        // check the setting of AmazonSNSEndpoint
        assertEquals("sns.ap-southeast-2.amazonaws.com", mock.getEndpoint());
        
        endpoint.stop();
    }
    
    @Test
    public void createEndpointWithMaximalConfiguration() throws Exception {
        SnsComponent component = new SnsComponent(context);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://MyTopic?amazonSNSEndpoint=sns.eu-west-1.amazonaws.com&accessKey=xxx&secretKey=yyy"
                + "&policy=%7B%22Version%22%3A%222008-10-17%22,%22Statement%22%3A%5B%7B%22Sid%22%3A%221%22,%22Effect%22%3A%22Allow%22,%22Principal%22%3A%7B%22AWS%22%3A%5B%22*%22%5D%7D,"
                + "%22Action%22%3A%5B%22sns%3ASubscribe%22%5D%7D%5D%7D&subject=The+subject+message");
        
        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("sns.eu-west-1.amazonaws.com", endpoint.getConfiguration().getAmazonSNSEndpoint());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNull(endpoint.getConfiguration().getAmazonSNSClient());
        assertEquals("The subject message", endpoint.getConfiguration().getSubject());
        assertEquals(
                "{\"Version\":\"2008-10-17\",\"Statement\":[{\"Sid\":\"1\",\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"sns:Subscribe\"]}]}",
                endpoint.getConfiguration().getPolicy());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAccessKeyConfiguration() throws Exception {
        SnsComponent component = new SnsComponent(context);
        component.createEndpoint("aws-sns://MyTopic?secretKey=yyy");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutSecretKeyConfiguration() throws Exception {
        SnsComponent component = new SnsComponent(context);
        component.createEndpoint("aws-sns://MyTopic?accessKey=xxx");
    }
}
