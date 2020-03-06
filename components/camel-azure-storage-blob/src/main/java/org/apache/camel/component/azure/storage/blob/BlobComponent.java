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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Azure Blob Storage component using azure java sdk v12.x
 */
@Component("azure-storage-blob")
public class BlobComponent extends DefaultComponent {

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

        final BlobConfiguration configuration = this.configuration != null ? this.configuration.copy() : new BlobConfiguration();

        final String[] parts = remaining.split("/");

        // only account name is being set
        configuration.setAccountName(parts[0]);

        // also container name is being set
        if (parts.length > 1) {
            configuration.setContainerName(parts[1]);
        }

        final BlobEndpoint endpoint = new BlobEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        validateConfigurations(configuration);

        return endpoint;
    }

    /**
     * Config
     */
    public BlobConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(BlobConfiguration configuration) {
        this.configuration = configuration;
    }

    private void validateConfigurations(final BlobConfiguration configuration) {

        // TODO: Add client
        if (configuration.getAccessKey() == null) {
            throw new IllegalArgumentException("Azure Storage accessKey or must be specified.");
        }
    }
}
