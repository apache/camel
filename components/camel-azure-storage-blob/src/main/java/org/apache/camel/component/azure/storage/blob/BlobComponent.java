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
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure Blob Storage component using azure java sdk v12.x
 */
@Component("azure-storage-blob")
public class BlobComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(BlobComponent.class);

    @Metadata
    private BlobConfiguration configuration = new BlobConfiguration();

    public BlobComponent() {
    }

    public BlobComponent(final CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("At least the account name must be specified.");
        }

        final BlobConfiguration config = this.configuration != null
                ? this.configuration.copy()
                : new BlobConfiguration();

        final String[] parts = remaining.split("/");

        // only account name is being set
        config.setAccountName(parts[0]);

        // also container name is being set
        if (parts.length > 1) {
            config.setContainerName(parts[1]);
        }

        final BlobEndpoint endpoint = new BlobEndpoint(uri, this, config);
        setProperties(endpoint, parameters);

        if (config.isAutoDiscoverClient()) {
            checkAndSetRegistryClient(config);
        }

        checkCredentials(config);
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

    private void checkCredentials(final BlobConfiguration configuration) {
        final BlobServiceClient client = configuration.getServiceClient();

        // if no azureBlobClient is provided fallback to credentials
        if (client == null) {
            Set<StorageSharedKeyCredential> storageSharedKeyCredentials
                    = getCamelContext().getRegistry().findByType(StorageSharedKeyCredential.class);
            if (storageSharedKeyCredentials.size() == 1) {
                configuration.setCredentials(storageSharedKeyCredentials.stream().findFirst().get());
            }
        }
    }

    private void checkAndSetRegistryClient(final BlobConfiguration configuration) {
        if (ObjectHelper.isEmpty(configuration.getServiceClient())) {
            final Set<BlobServiceClient> clients = getCamelContext().getRegistry().findByType(BlobServiceClient.class);
            if (clients.size() == 1) {
                configuration.setServiceClient(clients.stream().findFirst().get());
            } else if (clients.size() > 1) {
                LOG.info("More than one BlobServiceClient instance in the registry, make sure to have only one instance");
            } else {
                LOG.info("No BlobServiceClient instance in the registry");
            }
        } else {
            LOG.info("BlobServiceClient instance is already set at endpoint level: skipping the check in the registry");
        }
    }

    private void validateConfigurations(final BlobConfiguration configuration) {
        if (configuration.getServiceClient() == null
                && configuration.getAccessKey() == null
                && configuration.getCredentials() == null) {
            throw new IllegalArgumentException("Azure Storage accessKey or BlobServiceClient must be specified.");
        }
    }
}
