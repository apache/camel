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
package org.apache.camel.component.azure.storage.datalake;

import java.util.Map;
import java.util.Set;

import com.azure.core.credential.AzureSasCredential;
import com.azure.identity.ClientSecretCredential;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;
import org.apache.camel.util.ObjectHelper;

@Component("azure-storage-datalake")
public class DataLakeComponent extends HealthCheckComponent {

    @Metadata(description = "configuration object for datalake")
    private DataLakeConfiguration configuration = new DataLakeConfiguration();

    public DataLakeComponent() {
    }

    public DataLakeComponent(final CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().isEmpty()) {
            throw new IllegalArgumentException("At least the account name must be specified");
        }

        final DataLakeConfiguration configuration;
        if (this.configuration != null) {
            configuration = this.configuration.copy();
        } else {
            configuration = new DataLakeConfiguration();
        }

        final String[] details = remaining.split("/");

        configuration.setAccountName(details[0]);

        if (details.length > 1) {
            configuration.setFileSystemName(details[1]);
        }

        final DataLakeEndpoint endpoint = new DataLakeEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        setCredentialsFromRegistry(configuration);

        return endpoint;
    }

    public DataLakeConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(DataLakeConfiguration configuration) {
        this.configuration = configuration;
    }

    private void setCredentialsFromRegistry(final DataLakeConfiguration configuration) {
        if (ObjectHelper.isEmpty(configuration.getServiceClient())) {
            final Set<StorageSharedKeyCredential> storageSharedKeyCredentials
                    = getCamelContext().getRegistry().findByType(StorageSharedKeyCredential.class);
            final Set<ClientSecretCredential> clientSecretCredentials
                    = getCamelContext().getRegistry().findByType(ClientSecretCredential.class);
            final Set<AzureSasCredential> sasCredentials
                    = getCamelContext().getRegistry().findByType(AzureSasCredential.class);

            if (storageSharedKeyCredentials.size() == 1) {
                configuration.setSharedKeyCredential(storageSharedKeyCredentials.stream().findFirst().get());
            }
            if (clientSecretCredentials.size() == 1) {
                configuration.setClientSecretCredential(clientSecretCredentials.stream().findFirst().get());
            }
            if (sasCredentials.size() == 1) {
                configuration.setSasCredential(sasCredentials.stream().findFirst().get());
            }
        }
    }
}
