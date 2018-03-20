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
package org.apache.camel.component.aws.ses;

import com.amazonaws.regions.Regions;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SesComponentConfigurationTest extends CamelTestSupport {
    
    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        AmazonSESClientMock mock = new AmazonSESClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry())
            .bind("amazonSESClient", mock);
        
        SesComponent component = new SesComponent(context);
        SesEndpoint endpoint = (SesEndpoint) component.createEndpoint("aws-ses://from@example.com?amazonSESClient=#amazonSESClient&accessKey=xxx&secretKey=yyy");
        
        assertEquals("from@example.com", endpoint.getConfiguration().getFrom());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAmazonSESClient());
        assertNull(endpoint.getConfiguration().getTo());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getReturnPath());
        assertNull(endpoint.getConfiguration().getReplyToAddresses());
    }

    @Test
    public void createEndpointWithOnlyAccessKeyAndSecretKey() throws Exception {
        SesComponent component = new SesComponent(context);
        SesEndpoint endpoint = (SesEndpoint) component.createEndpoint("aws-ses://from@example.com?accessKey=xxx&secretKey=yyy");

        assertEquals("from@example.com", endpoint.getConfiguration().getFrom());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonSESClient());
        assertNull(endpoint.getConfiguration().getTo());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getReturnPath());
        assertNull(endpoint.getConfiguration().getReplyToAddresses());
    }
    
    @Test
    public void createEndpointWithMinimalConfigurationAndProvidedClient() throws Exception {
        AmazonSESClientMock mock = new AmazonSESClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry())
            .bind("amazonSESClient", mock);
        
        SesComponent component = new SesComponent(context);
        SesEndpoint endpoint = (SesEndpoint) component.createEndpoint("aws-ses://from@example.com?"
                + "amazonSESClient=#amazonSESClient");
        
        assertEquals("from@example.com", endpoint.getConfiguration().getFrom());
        assertNull(endpoint.getConfiguration().getAccessKey());
        assertNull(endpoint.getConfiguration().getSecretKey());
        assertSame(mock, endpoint.getConfiguration().getAmazonSESClient());
        assertNull(endpoint.getConfiguration().getTo());
        assertNull(endpoint.getConfiguration().getSubject());
        assertNull(endpoint.getConfiguration().getReturnPath());
        assertNull(endpoint.getConfiguration().getReplyToAddresses());
    }

    @Test
    public void createEndpointWithMaximalConfiguration() throws Exception {
        AmazonSESClientMock mock = new AmazonSESClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry())
            .bind("amazonSESClient", mock);
        
        SesComponent component = new SesComponent(context);
        SesEndpoint endpoint = (SesEndpoint) component.createEndpoint("aws-ses://from@example.com?amazonSESClient=#amazonSESClient&accessKey=xxx"
            + "&secretKey=yyy&to=to1@example.com,to2@example.com&subject=Subject"
            + "&returnPath=bounce@example.com&replyToAddresses=replyTo1@example.com,replyTo2@example.com");
        
        assertEquals("from@example.com", endpoint.getConfiguration().getFrom());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAmazonSESClient());
        assertEquals(2, endpoint.getConfiguration().getTo().size());
        assertTrue(endpoint.getConfiguration().getTo().contains("to1@example.com"));
        assertTrue(endpoint.getConfiguration().getTo().contains("to2@example.com"));
        assertEquals("Subject", endpoint.getConfiguration().getSubject());
        assertEquals("bounce@example.com", endpoint.getConfiguration().getReturnPath());
        assertEquals(2, endpoint.getConfiguration().getReplyToAddresses().size());
        assertTrue(endpoint.getConfiguration().getReplyToAddresses().contains("replyTo1@example.com"));
        assertTrue(endpoint.getConfiguration().getReplyToAddresses().contains("replyTo2@example.com"));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutSourceName() throws Exception {
        SesComponent component = new SesComponent(context);
        component.createEndpoint("aws-ses:// ");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAmazonSESClientConfiguration() throws Exception {
        SesComponent component = new SesComponent(context);
        component.createEndpoint("aws-ses://from@example.com");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAccessKeyConfiguration() throws Exception {
        SesComponent component = new SesComponent(context);
        component.createEndpoint("aws-ses://from@example.com?secretKey=yyy");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutSecretKeyConfiguration() throws Exception {
        SesComponent component = new SesComponent(context);
        component.createEndpoint("aws-ses://from@example.com?accessKey=xxx");
    }
    
    @Test
    public void createEndpointWithComponentElements() throws Exception {
        SesComponent component = new SesComponent(context);
        component.setAccessKey("XXX");
        component.setSecretKey("YYY");
        SesEndpoint endpoint = (SesEndpoint)component.createEndpoint("aws-ses://from@example.com");
        
        assertEquals("from@example.com", endpoint.getConfiguration().getFrom());
        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
    }
    
    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        SesComponent component = new SesComponent(context);
        component.setAccessKey("XXX");
        component.setSecretKey("YYY");
        component.setRegion(Regions.US_WEST_1.toString());
        SesEndpoint endpoint = (SesEndpoint)component.createEndpoint("aws-ses://from@example.com?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");
        
        assertEquals("from@example.com", endpoint.getConfiguration().getFrom());
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
    }
    
    @Test
    public void createEndpointWithoutSecretKeyAndAccessKeyConfiguration() throws Exception {
        AmazonSESClientMock mock = new AmazonSESClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry())
            .bind("amazonSESClient", mock);
        
        SesComponent component = new SesComponent(context);
        component.createEndpoint("aws-ses://from@example.com?amazonSESClient=#amazonSESClient");
    }
}