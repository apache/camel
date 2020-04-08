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
package org.apache.camel.component.azure.blob;

import java.util.Map;
import java.util.Set;

import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.StorageCredentialsAnonymous;
import com.microsoft.azure.storage.blob.CloudBlob;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("azure-blob")
public class BlobServiceComponent extends DefaultComponent {

    public static final String MISSING_BLOB_CREDENTIALS_EXCEPTION_MESSAGE =
            "One of azureBlobClient, credentials or both credentialsAccountName and credentialsAccountKey must be specified";

    @Metadata(label = "advanced")
    private BlobServiceConfiguration configuration;

    public BlobServiceComponent() {
    }

    public BlobServiceComponent(CamelContext context) {
        super(context);
        this.configuration = new BlobServiceConfiguration();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final BlobServiceConfiguration configuration = this.configuration != null ? this.configuration.copy() : new BlobServiceConfiguration();

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

        BlobServiceEndpoint endpoint = new BlobServiceEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        checkAndSetRegistryClient(configuration);
        checkCredentials(configuration);

        return endpoint;
    }

    public BlobServiceConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The Blob Service configuration
     */
    public void setConfiguration(BlobServiceConfiguration configuration) {
        this.configuration = configuration;
    }

    private void checkCredentials(BlobServiceConfiguration cfg) {
        CloudBlob client = cfg.getAzureBlobClient();

        //if no azureBlobClient is provided fallback to credentials
        StorageCredentials creds = client == null ? cfg.getAccountCredentials()
                : client.getServiceClient().getCredentials();
        if ((creds == null || creds instanceof StorageCredentialsAnonymous) && !cfg.isPublicForRead()) {
            throw new IllegalArgumentException(MISSING_BLOB_CREDENTIALS_EXCEPTION_MESSAGE);
        }
    }

    private void checkAndSetRegistryClient(BlobServiceConfiguration configuration) {
        Set<CloudBlob> clients = getCamelContext().getRegistry().findByType(CloudBlob.class);
        if (clients.size() == 1) {
            configuration.setAzureBlobClient(clients.stream().findFirst().get());
        }
    }
}
