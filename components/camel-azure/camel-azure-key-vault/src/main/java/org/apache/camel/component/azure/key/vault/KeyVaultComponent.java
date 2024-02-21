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
package org.apache.camel.component.azure.key.vault;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Azure Key Vault component
 */
@Component("azure-key-vault")
public class KeyVaultComponent extends DefaultComponent {

    @Metadata
    private KeyVaultConfiguration configuration = new KeyVaultConfiguration();

    public KeyVaultComponent() {
    }

    public KeyVaultComponent(final CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.isBlank()) {
            throw new IllegalArgumentException("A vault name must be specified.");
        }

        final KeyVaultConfiguration epConfiguration
                = this.configuration != null ? this.configuration.copy() : new KeyVaultConfiguration();

        // set account or topic name
        epConfiguration.setVaultName(remaining);

        final KeyVaultEndpoint endpoint = new KeyVaultEndpoint(uri, this, epConfiguration);
        setProperties(endpoint, parameters);

        if (epConfiguration.getSecretClient() == null
                && (epConfiguration.getClientId() == null || epConfiguration.getClientSecret() == null
                        || epConfiguration.getTenantId() == null)) {
            throw new IllegalArgumentException(
                    "Azure Secret Client or client Id, client secret and tenant Id must be specified");
        }

        return endpoint;
    }
}
