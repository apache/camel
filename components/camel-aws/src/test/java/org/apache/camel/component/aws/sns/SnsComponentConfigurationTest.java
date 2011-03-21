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
    }
    
    @Test
    public void createEndpointWithMinimalConfigurationAndProvidedClient() throws Exception {
        AmazonSNSClientMock mock = new AmazonSNSClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("amazonSNSClient", mock);
        
        SnsComponent component = new SnsComponent(context);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://MyTopic?amazonSNSClient=#amazonSNSClient");
        
        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertNull(endpoint.getConfiguration().getAccessKey());
        assertNull(endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNull(endpoint.getConfiguration().getSubject());
        
        endpoint.start();
        
        assertEquals("arn:aws:sns:us-east-1:541925086079:MyTopic", endpoint.getConfiguration().getTopicArn());
        
        endpoint.stop();
    }
    
    @Test
    public void createEndpointWithMaximalConfiguration() throws Exception {
        SnsComponent component = new SnsComponent(context);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://MyTopic?accessKey=xxx&secretKey=yyy&subject=The+subject+message");
        
        assertEquals("MyTopic", endpoint.getConfiguration().getTopicName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getTopicArn());
        assertNull(endpoint.getConfiguration().getAmazonSNSClient());
        assertEquals("The subject message", endpoint.getConfiguration().getSubject());
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