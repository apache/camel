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
package org.apache.camel.component.azure.queue;

import java.net.URI;

import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.core.Base64;
import com.microsoft.azure.storage.queue.CloudQueue;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class QueueServiceComponentConfigurationTest extends CamelTestSupport {
    
    @Test
    public void testCreateEndpointWithMinConfigForClientOnly() throws Exception {
        CloudQueue client = 
            new CloudQueue(URI.create("https://camelazure.queue.core.windows.net/testqueue/messages"),
                           newAccountKeyCredentials());
        
        JndiRegistry registry = (JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry();
        registry.bind("azureQueueClient", client);
        
        QueueServiceComponent component = new QueueServiceComponent(context);
        QueueServiceEndpoint endpoint = 
            (QueueServiceEndpoint) component.createEndpoint("azure-queue://camelazure/testqueue?azureQueueClient=#azureQueueClient");
        
        doTestCreateEndpointWithMinConfig(endpoint, true);
    }
    
    @Test
    public void testCreateEndpointWithMinConfigForCredsOnly() throws Exception {
        registerCredentials();
        
        QueueServiceComponent component = new QueueServiceComponent(context);
        QueueServiceEndpoint endpoint = 
            (QueueServiceEndpoint) component.createEndpoint("azure-queue://camelazure/testqueue?credentials=#creds");
        
        doTestCreateEndpointWithMinConfig(endpoint, false);
    }
    
    @Test
    public void testCreateEndpointWithMaxConfig() throws Exception {
        registerCredentials();
        
        QueueServiceComponent component = new QueueServiceComponent(context);
        QueueServiceEndpoint endpoint = 
            (QueueServiceEndpoint) component.createEndpoint("azure-queue://camelazure/testqueue?credentials=#creds"
                + "&operation=addMessage&queuePrefix=prefix&messageTimeToLive=100&messageVisibilityDelay=10");
        
        doTestCreateEndpointWithMaxConfig(endpoint, false);
    }
    
    private void doTestCreateEndpointWithMinConfig(QueueServiceEndpoint endpoint, boolean clientExpected)
        throws Exception {
        assertEquals("camelazure", endpoint.getConfiguration().getAccountName());
        assertEquals("testqueue", endpoint.getConfiguration().getQueueName());
        if (clientExpected) {
            assertNotNull(endpoint.getConfiguration().getAzureQueueClient());
            assertNull(endpoint.getConfiguration().getCredentials());
        } else {
            assertNull(endpoint.getConfiguration().getAzureQueueClient());
            assertNotNull(endpoint.getConfiguration().getCredentials());
        }
        assertEquals(QueueServiceOperations.listQueues, endpoint.getConfiguration().getOperation());
        
        assertNull(endpoint.getConfiguration().getQueuePrefix());
        assertEquals(0, endpoint.getConfiguration().getMessageTimeToLive());
        assertEquals(0, endpoint.getConfiguration().getMessageVisibilityDelay());
        createConsumer(endpoint);
    }
    
    private void doTestCreateEndpointWithMaxConfig(QueueServiceEndpoint endpoint, boolean clientExpected)
        throws Exception {
        assertEquals("camelazure", endpoint.getConfiguration().getAccountName());
        assertEquals("testqueue", endpoint.getConfiguration().getQueueName());
        if (clientExpected) {
            assertNotNull(endpoint.getConfiguration().getAzureQueueClient());
            assertNull(endpoint.getConfiguration().getCredentials());
        } else {
            assertNull(endpoint.getConfiguration().getAzureQueueClient());
            assertNotNull(endpoint.getConfiguration().getCredentials());
        }
        assertEquals(QueueServiceOperations.addMessage, endpoint.getConfiguration().getOperation());
        
        assertEquals("prefix", endpoint.getConfiguration().getQueuePrefix());
        assertEquals(100, endpoint.getConfiguration().getMessageTimeToLive());
        assertEquals(10, endpoint.getConfiguration().getMessageVisibilityDelay());
        
        createConsumer(endpoint);
    }
    
    @Test
    public void testNoCredentials() throws Exception {
        QueueServiceComponent component = new QueueServiceComponent(context);
        try {
            component.createEndpoint("azure-queue://camelazure/testqueue");
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("Credentials must be specified.", ex.getMessage());
        }
    }
    
    @Test
    public void testTooManyPathSegments() throws Exception {
        QueueServiceComponent component = new QueueServiceComponent(context);
        try {
            component.createEndpoint("azure-queue://camelazure/testqueue/1");
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("Only the account and queue names must be specified.", ex.getMessage());
        }
    }
    
    @Test
    public void testTooFewPathSegments() throws Exception {
        QueueServiceComponent component = new QueueServiceComponent(context);
        try {
            component.createEndpoint("azure-queue://camelazure?operation=addMessage");
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("The queue name must be specified.", ex.getMessage());
        }
    }
    
    
    private static void createConsumer(Endpoint endpoint) throws Exception {
        endpoint.createConsumer(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                // noop
            }
        });
    }
    
    private void registerCredentials() {
        JndiRegistry registry = (JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry();
        registry.bind("creds", newAccountKeyCredentials());
    }
    private StorageCredentials newAccountKeyCredentials() {
        return new StorageCredentialsAccountAndKey("camelazure", 
                                                   Base64.encode("key".getBytes()));
    }
    
}
