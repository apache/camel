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

import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.blob.CloudAppendBlob;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.core.Base64;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class BlobServiceUtilTest extends CamelTestSupport {

    @Test
    public void testPrepareUri() throws Exception {
        registerCredentials();
        
        BlobServiceComponent component = new BlobServiceComponent(context);
        BlobServiceEndpoint endpoint = 
            (BlobServiceEndpoint) component.createEndpoint("azure-blob://camelazure/container/blob?credentials=#creds");
        URI uri = 
            BlobServiceUtil.prepareStorageBlobUri(endpoint.getConfiguration());
        assertEquals("https://camelazure.blob.core.windows.net/container/blob", uri.toString());
    }

    @Test
    public void testGetConfiguredClient() throws Exception {
        CloudBlockBlob client = 
            new CloudBlockBlob(URI.create("https://camelazure.blob.core.windows.net/container/blob"));
        
        JndiRegistry registry = (JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry();
        registry.bind("azureBlobClient", client);
        
        BlobServiceComponent component = new BlobServiceComponent(context);
        BlobServiceEndpoint endpoint = 
            (BlobServiceEndpoint) component.createEndpoint("azure-blob://camelazure/container/blob?azureBlobClient=#azureBlobClient&publicForRead=true");
        assertSame(client, BlobServiceUtil.getConfiguredClient(endpoint.getConfiguration()));
    }
    @Test
    public void testGetConfiguredClientTypeMismatch() throws Exception {
        CloudBlockBlob client = 
            new CloudBlockBlob(URI.create("https://camelazure.blob.core.windows.net/container/blob"));
        
        JndiRegistry registry = (JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry();
        registry.bind("azureBlobClient", client);
        
        BlobServiceComponent component = new BlobServiceComponent(context);
        BlobServiceEndpoint endpoint = 
            (BlobServiceEndpoint) component.createEndpoint("azure-blob://camelazure/container/blob?azureBlobClient=#azureBlobClient&publicForRead=true"
                                                           + "&blobType=appendBlob");
        try {
            BlobServiceUtil.getConfiguredClient(endpoint.getConfiguration());
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("Invalid Client Type", ex.getMessage());
        }
    }
    @Test
    public void testGetConfiguredClientUriMismatch() throws Exception {
        CloudAppendBlob client = 
            new CloudAppendBlob(URI.create("https://camelazure.blob.core.windows.net/container/blob"));
        
        JndiRegistry registry = (JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry();
        registry.bind("azureBlobClient", client);
        
        BlobServiceComponent component = new BlobServiceComponent(context);
        BlobServiceEndpoint endpoint = 
            (BlobServiceEndpoint) component.createEndpoint("azure-blob://camelazure/container/blob2?azureBlobClient=#azureBlobClient&publicForRead=true"
                                                           + "&blobType=appendBlob");
        try {
            BlobServiceUtil.getConfiguredClient(endpoint.getConfiguration());
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("Invalid Client URI", ex.getMessage());
        }
    }

    private void registerCredentials() {
        StorageCredentials creds = new StorageCredentialsAccountAndKey("camelazure", 
                                                                       Base64.encode("key".getBytes()));
        JndiRegistry registry = (JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry();
        registry.bind("creds", creds);
    }

}
