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
package org.apache.camel.component.azure.blob;

import java.net.URI;
import java.util.Collections;

import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.StorageCredentialsAnonymous;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.core.Base64;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class BlobServiceComponentConfigurationTest extends CamelTestSupport {
    
    @Test
    public void testCreateEndpointWithMinConfigForClientOnly() throws Exception {
        CloudBlockBlob client = 
            new CloudBlockBlob(URI.create("https://camelazure.blob.core.windows.net/container/blob"),
                               newAccountKeyCredentials());
        
        JndiRegistry registry = (JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry();
        registry.bind("azureBlobClient", client);
        
        BlobServiceComponent component = new BlobServiceComponent(context);
        BlobServiceEndpoint endpoint = 
            (BlobServiceEndpoint) component.createEndpoint("azure-blob://camelazure/container/blob?azureBlobClient=#azureBlobClient");
        
        doTestCreateEndpointWithMinConfig(endpoint, true);
    }
    
    @Test
    public void testCreateEndpointWithMinConfigForCredsOnly() throws Exception {
        registerCredentials();
        
        BlobServiceComponent component = new BlobServiceComponent(context);
        BlobServiceEndpoint endpoint = 
            (BlobServiceEndpoint) component.createEndpoint("azure-blob://camelazure/container/blob?credentials=#creds");
        
        doTestCreateEndpointWithMinConfig(endpoint, false);
    }
    
    private void doTestCreateEndpointWithMinConfig(BlobServiceEndpoint endpoint, boolean clientExpected)
        throws Exception {
        assertEquals("camelazure", endpoint.getConfiguration().getAccountName());
        assertEquals("container", endpoint.getConfiguration().getContainerName());
        assertEquals("blob", endpoint.getConfiguration().getBlobName());
        if (clientExpected) {
            assertNotNull(endpoint.getConfiguration().getAzureBlobClient());
            assertNull(endpoint.getConfiguration().getCredentials());
        } else {
            assertNull(endpoint.getConfiguration().getAzureBlobClient());
            assertNotNull(endpoint.getConfiguration().getCredentials());
        }
        
        assertEquals(BlobType.blockblob, endpoint.getConfiguration().getBlobType());
        assertNull(endpoint.getConfiguration().getBlobPrefix());
        assertNull(endpoint.getConfiguration().getFileDir());
        assertEquals(Long.valueOf(0L), endpoint.getConfiguration().getBlobOffset());
        assertNull(endpoint.getConfiguration().getBlobMetadata());
        assertNull(endpoint.getConfiguration().getDataLength());
        assertEquals(BlobServiceOperations.listBlobs, endpoint.getConfiguration().getOperation());
        assertEquals(0, endpoint.getConfiguration().getStreamWriteSize());
        assertEquals(0, endpoint.getConfiguration().getStreamReadSize());
        assertTrue(endpoint.getConfiguration().isCloseStreamAfterRead());
        assertTrue(endpoint.getConfiguration().isCloseStreamAfterWrite());
        assertFalse(endpoint.getConfiguration().isPublicForRead());
        assertTrue(endpoint.getConfiguration().isUseFlatListing());
        
        createConsumer(endpoint);
    }
    
    @Test
    public void testCreateEndpointWithMaxConfig() throws Exception {
        registerCredentials();
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry())
            .bind("metadata", Collections.emptyMap());
        
        BlobServiceComponent component = new BlobServiceComponent(context);
        BlobServiceEndpoint endpoint = 
            (BlobServiceEndpoint) component.createEndpoint(
            "azure-blob://camelazure/container/blob?credentials=#creds&blobType=pageblob"
            + "&blobPrefix=blob1&fileDir=/tmp&blobOffset=512&operation=clearPageBlob&dataLength=1024"
            + "&streamWriteSize=512&streamReadSize=1024&closeStreamAfterRead=false&closeStreamAfterWrite=false"
            + "&publicForRead=true&useFlatListing=false&blobMetadata=#metadata");
        
        assertEquals("camelazure", endpoint.getConfiguration().getAccountName());
        assertEquals("container", endpoint.getConfiguration().getContainerName());
        assertEquals("blob", endpoint.getConfiguration().getBlobName());
        assertNull(endpoint.getConfiguration().getAzureBlobClient());
        assertNotNull(endpoint.getConfiguration().getCredentials());
        
        assertEquals(BlobType.pageblob, endpoint.getConfiguration().getBlobType());
        assertEquals("blob1", endpoint.getConfiguration().getBlobPrefix());
        assertEquals("/tmp", endpoint.getConfiguration().getFileDir());
        assertEquals(Long.valueOf(512L), endpoint.getConfiguration().getBlobOffset());
        assertNotNull(endpoint.getConfiguration().getBlobMetadata());
        assertEquals(Long.valueOf(1024L), endpoint.getConfiguration().getDataLength());
        assertEquals(BlobServiceOperations.clearPageBlob, endpoint.getConfiguration().getOperation());
        assertEquals(512, endpoint.getConfiguration().getStreamWriteSize());
        assertEquals(1024, endpoint.getConfiguration().getStreamReadSize());
        assertFalse(endpoint.getConfiguration().isCloseStreamAfterRead());
        assertFalse(endpoint.getConfiguration().isCloseStreamAfterWrite());
        assertTrue(endpoint.getConfiguration().isPublicForRead());
        assertFalse(endpoint.getConfiguration().isUseFlatListing());
    }
    
    @Test
    public void testNoClientAndCredentials() throws Exception {
        BlobServiceComponent component = new BlobServiceComponent(context);
        try {
            component.createEndpoint("azure-blob://camelazure/container/blob");
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("Credentials must be specified.", ex.getMessage());
        }
    }
    @Test
    public void testNoClientAndCredentialsPublicForRead() throws Exception {
        BlobServiceComponent component = new BlobServiceComponent(context);
        BlobServiceEndpoint endpoint = 
            (BlobServiceEndpoint) component.createEndpoint("azure-blob://camelazure/container/blob?publicForRead=true");
        assertTrue(endpoint.getConfiguration().isPublicForRead());
    }
    
    @Test
    public void testClientWithoutCredentials() throws Exception {
        CloudBlockBlob client = 
            new CloudBlockBlob(URI.create("https://camelazure.blob.core.windows.net/container/blob"));
        
        doTestClientWithoutCredentials(client);
    }
    @Test
    public void testClientWithoutAnonymousCredentials() throws Exception {
        CloudBlockBlob client = 
            new CloudBlockBlob(URI.create("https://camelazure.blob.core.windows.net/container/blob"),
                               StorageCredentialsAnonymous.ANONYMOUS);
        
        doTestClientWithoutCredentials(client);
    }
    @Test
    public void testClientWithoutCredentialsPublicRead() throws Exception {
        CloudBlockBlob client = 
            new CloudBlockBlob(URI.create("https://camelazure.blob.core.windows.net/container/blob"));
        
        JndiRegistry registry = (JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry();
        registry.bind("azureBlobClient", client);
        
        BlobServiceComponent component = new BlobServiceComponent(context);
        BlobServiceEndpoint endpoint = 
            (BlobServiceEndpoint) component.createEndpoint("azure-blob://camelazure/container/blob?azureBlobClient=#azureBlobClient&publicForRead=true");
        assertTrue(endpoint.getConfiguration().isPublicForRead());
    }
    private void doTestClientWithoutCredentials(CloudBlob client) throws Exception {
        JndiRegistry registry = (JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry();
        registry.bind("azureBlobClient", client);
        
        BlobServiceComponent component = new BlobServiceComponent(context);
        try {
            component.createEndpoint("azure-blob://camelazure/container/blob?azureBlobClient=#azureBlobClient");
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("Credentials must be specified.", ex.getMessage());
        }
    }
    
    @Test
    public void testNoBlobNameProducerWithOp() throws Exception {
        registerCredentials();
        BlobServiceComponent component = new BlobServiceComponent(context);
        BlobServiceEndpoint endpointWithOp = 
            (BlobServiceEndpoint) component.createEndpoint(
            "azure-blob://camelazure/container?operation=deleteBlob&credentials=#creds");
        try {
            endpointWithOp.createProducer();
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("Blob name must be specified.", ex.getMessage());
        }
    }
    @Test
    public void testNoBlobNameProducerDefaultOp() throws Exception {
        registerCredentials();
        BlobServiceComponent component = new BlobServiceComponent(context);
        BlobServiceEndpoint endpoint = 
            (BlobServiceEndpoint) component.createEndpoint(
            "azure-blob://camelazure/container?credentials=#creds");
        endpoint.createProducer();
        assertEquals(BlobServiceOperations.listBlobs, endpoint.getConfiguration().getOperation());
    }
    
    @Test
    public void testNoBlobNameConsumer() throws Exception {
        registerCredentials();
        BlobServiceComponent component = new BlobServiceComponent(context);
        BlobServiceEndpoint endpoint = 
            (BlobServiceEndpoint) component.createEndpoint(
            "azure-blob://camelazure/container?credentials=#creds");
        try {
            createConsumer(endpoint);
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("Blob name must be specified.", ex.getMessage());
        }
    }
    
    @Test
    public void testTooFewPathSegments() throws Exception {
        BlobServiceComponent component = new BlobServiceComponent(context);
        try {
            component.createEndpoint("azure-blob://camelazure");
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("At least the account and container names must be specified.", ex.getMessage());
        }
    }
    
    @Test
    public void testHierarchicalBlobName() throws Exception {
        registerCredentials();
        BlobServiceComponent component = new BlobServiceComponent(context);
        BlobServiceEndpoint endpoint = 
            (BlobServiceEndpoint)component.createEndpoint("azure-blob://camelazure/component1/blob/sub?credentials=#creds");
        assertEquals("blob/sub", endpoint.getConfiguration().getBlobName());
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
