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
package org.apache.camel.component.aws.sdb;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SdbComponentConfigurationTest extends CamelTestSupport {
    
    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        AmazonSDBClientMock mock = new AmazonSDBClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry())
            .bind("amazonSDBClient", mock);
        
        SdbComponent component = new SdbComponent(context);
        SdbEndpoint endpoint = (SdbEndpoint) component.createEndpoint(
                "aws-sdb://TestDomain?amazonSDBClient=#amazonSDBClient&accessKey=xxx&secretKey=yyy");
        
        assertEquals("TestDomain", endpoint.getConfiguration().getDomainName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAmazonSDBClient());
        assertEquals(SdbOperations.PutAttributes, endpoint.getConfiguration().getOperation());
        assertNull(endpoint.getConfiguration().getAmazonSdbEndpoint());
        assertFalse(endpoint.getConfiguration().isConsistentRead());
        assertNull(endpoint.getConfiguration().getMaxNumberOfDomains());
    }

    @Test
    public void createEndpointWithOnlyAccessKeyAndSecretKey() throws Exception {
        SdbComponent component = new SdbComponent(context);
        SdbEndpoint endpoint = (SdbEndpoint) component.createEndpoint(
            "aws-sdb://TestDomain?accessKey=xxx&secretKey=yyy");

        assertEquals("TestDomain", endpoint.getConfiguration().getDomainName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonSDBClient());
        assertEquals(SdbOperations.PutAttributes, endpoint.getConfiguration().getOperation());
        assertNull(endpoint.getConfiguration().getAmazonSdbEndpoint());
        assertFalse(endpoint.getConfiguration().isConsistentRead());
        assertNull(endpoint.getConfiguration().getMaxNumberOfDomains());
    }
    
    @Test
    public void createEndpointWithMinimalConfigurationAndProvidedClient() throws Exception {
        AmazonSDBClientMock mock = new AmazonSDBClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry())
            .bind("amazonSDBClient", mock);
        
        SdbComponent component = new SdbComponent(context);
        SdbEndpoint endpoint = (SdbEndpoint) component.createEndpoint("aws-sdb://TestDomain?"
                + "amazonSDBClient=#amazonSDBClient");
        
        assertEquals("TestDomain", endpoint.getConfiguration().getDomainName());
        assertNull(endpoint.getConfiguration().getAccessKey());
        assertNull(endpoint.getConfiguration().getSecretKey());
        assertSame(mock, endpoint.getConfiguration().getAmazonSDBClient());
        assertEquals(SdbOperations.PutAttributes, endpoint.getConfiguration().getOperation());
        assertNull(endpoint.getConfiguration().getAmazonSdbEndpoint());
        assertFalse(endpoint.getConfiguration().isConsistentRead());
        assertNull(endpoint.getConfiguration().getMaxNumberOfDomains());
    }

    @Test
    public void createEndpointWithMaximalConfiguration() throws Exception {
        AmazonSDBClientMock mock = new AmazonSDBClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry())
            .bind("amazonSDBClient", mock);
        SdbComponent component = new SdbComponent(context);
        SdbEndpoint endpoint = (SdbEndpoint) component.createEndpoint(
                "aws-sdb://TestDomain?amazonSDBClient=#amazonSDBClient&accessKey=xxx&secretKey=yyy&operation=DeleteAttributes&consistentRead=true"
                + "&maxNumberOfDomains=5");
        
        assertEquals("TestDomain", endpoint.getConfiguration().getDomainName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAmazonSDBClient());
        assertEquals(SdbOperations.DeleteAttributes, endpoint.getConfiguration().getOperation());
        assertNull(endpoint.getConfiguration().getAmazonSdbEndpoint());
        assertTrue(endpoint.getConfiguration().isConsistentRead());
        assertEquals(new Integer(5), endpoint.getConfiguration().getMaxNumberOfDomains());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutDomainName() throws Exception {
        SdbComponent component = new SdbComponent(context);
        component.createEndpoint("aws-sdb:// ");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAmazonSDBClientConfiguration() throws Exception {
        SdbComponent component = new SdbComponent(context);
        component.createEndpoint("aws-sdb://TestDomain");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAccessKeyConfiguration() throws Exception {
        SdbComponent component = new SdbComponent(context);
        component.createEndpoint("aws-sdb://TestDomain?secretKey=yyy");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutSecretKeyConfiguration() throws Exception {
        SdbComponent component = new SdbComponent(context);
        component.createEndpoint("aws-sdb://TestDomain?accessKey=xxx");
    }
    
    @Test
    public void createEndpointWithoutSecretKeyAndAccessKeyConfiguration() throws Exception {
        AmazonSDBClientMock mock = new AmazonSDBClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry())
            .bind("amazonSDBClient", mock);
        
        SdbComponent component = new SdbComponent(context);
        component.createEndpoint("aws-sdb://TestDomain?amazonSDBClient=#amazonSDBClient");
    }
}