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

import java.util.Map;

import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.StorageCredentialsAnonymous;
import com.microsoft.azure.storage.blob.CloudBlob;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

public class BlobServiceComponent extends UriEndpointComponent {
    
    public BlobServiceComponent() {
        super(BlobServiceEndpoint.class);
    }

    public BlobServiceComponent(CamelContext context) {
        super(context, BlobServiceEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        BlobServiceConfiguration configuration = new BlobServiceConfiguration();
        setProperties(configuration, parameters);

        String[] parts = null;
        if (remaining != null) {
            parts = remaining.split("/"); 
        }
        if (parts == null || parts.length < 2) {
            throw new IllegalArgumentException("At least the account and container names must be specified.");
        }
        
        configuration.setAccountName(parts[0]);
        configuration.setContainerName(parts[1]);
        
        if (parts.length > 2) {
            // Blob names can contain forward slashes
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                sb.append(parts[i]);
                if (i + 1 < parts.length) {
                    sb.append('/');
                }
            }
            configuration.setBlobName(sb.toString());
        }
        
        checkCredentials(configuration);
        
        BlobServiceEndpoint endpoint = new BlobServiceEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }
    
    private void checkCredentials(BlobServiceConfiguration cfg) {
        CloudBlob client = cfg.getAzureBlobClient();
        StorageCredentials creds = client == null ? cfg.getCredentials() 
            : client.getServiceClient().getCredentials(); 
        if ((creds == null || creds instanceof StorageCredentialsAnonymous)
            && !cfg.isPublicForRead()) {
            throw new IllegalArgumentException("Credentials must be specified.");
        }
    }
}
