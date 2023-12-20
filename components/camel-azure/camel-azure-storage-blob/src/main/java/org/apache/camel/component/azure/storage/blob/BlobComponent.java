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
package org.apache.camel.component.azure.storage.blob;

import java.util.Map;
import java.util.Set;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;

import static org.apache.camel.component.azure.storage.blob.CredentialType.*;

/**
 * Azure Blob Storage component using azure java sdk v12.x
 */
@Component("azure-storage-blob")
public class BlobComponent extends HealthCheckComponent {
    @Metadata
    private BlobConfiguration configuration = new BlobConfiguration();

    public BlobComponent() {
    }

    public BlobComponent(final CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.isBlank()) {
            throw new IllegalArgumentException("At least the account name must be specified.");
        }

        final BlobConfiguration config = this.configuration != null ? this.configuration.copy() : new BlobConfiguration();

        final String[] parts = remaining.split("/");

        // only account name is being set
        config.setAccountName(parts[0]);

        // also container name is being set
        if (parts.length > 1) {
            config.setContainerName(parts[1]);
        }

        final BlobEndpoint endpoint = new BlobEndpoint(uri, this, config);
        setProperties(endpoint, parameters);

        initCredentialConfig(config);
        validateConfigurations(config);

        return endpoint;
    }

    /**
     * The component configurations
     */
    public BlobConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(BlobConfiguration configuration) {
        this.configuration = configuration;
    }

    private void initCredentialConfig(final BlobConfiguration configuration) {
        final BlobServiceClient client = configuration.getServiceClient();

        if (client == null) {
            //default to AZURE_AD
            if (configuration.getCredentialType() == null) {
                configuration.setCredentialType(AZURE_IDENTITY);
            } else if (SHARED_KEY_CREDENTIAL.equals(configuration.getCredentialType())) {
                Set<StorageSharedKeyCredential> storageSharedKeyCredentials
                        = getCamelContext().getRegistry().findByType(StorageSharedKeyCredential.class);
                storageSharedKeyCredentials.stream().findFirst().ifPresent(configuration::setCredentials);
            } else if (AZURE_SAS.equals(configuration.getCredentialType())) {
                configuration.setCredentialType(AZURE_SAS);
            }
        }
    }

    private void validateConfigurations(final BlobConfiguration configuration) {
        if (configuration.getServiceClient() == null) {
            if (SHARED_KEY_CREDENTIAL.equals(configuration.getCredentialType()) && configuration.getCredentials() == null) {
                throw new IllegalArgumentException("When using shared key credential, credentials must be provided.");
            } else if (SHARED_ACCOUNT_KEY.equals(configuration.getCredentialType()) && configuration.getAccessKey() == null) {
                throw new IllegalArgumentException("When using shared account key, access key must be provided.");
            } else if (AZURE_SAS.equals(configuration.getCredentialType()) && configuration.getSasToken() == null) {
                throw new IllegalArgumentException("When using Azure SAS, SAS Token must be provided.");
            }
        }
    }
}
