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

package org.apache.camel.component.cyberark.vault;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * Component for accessing secrets in CyberArk Conjur Vault.
 */
@Component("cyberark-vault")
public class CyberArkVaultComponent extends DefaultComponent {

    @Metadata
    private CyberArkVaultConfiguration configuration = new CyberArkVaultConfiguration();

    public CyberArkVaultComponent() {}

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        CyberArkVaultConfiguration configuration =
                this.configuration != null ? this.configuration.copy() : new CyberArkVaultConfiguration();

        CyberArkVaultEndpoint endpoint = new CyberArkVaultEndpoint(uri, this, configuration);
        endpoint.getConfiguration().setSecretId(remaining);
        setProperties(endpoint, parameters);

        // Validate required parameters
        if (configuration.getConjurClient() == null) {
            if (ObjectHelper.isEmpty(configuration.getUrl())) {
                throw new IllegalArgumentException("CyberArk Conjur URL must be specified");
            }
            if (ObjectHelper.isEmpty(configuration.getAccount())) {
                throw new IllegalArgumentException("CyberArk Conjur account must be specified");
            }

            // Validate authentication
            boolean hasAuthToken = ObjectHelper.isNotEmpty(configuration.getAuthToken());
            boolean hasApiKey = ObjectHelper.isNotEmpty(configuration.getApiKey());
            boolean hasCredentials = ObjectHelper.isNotEmpty(configuration.getUsername())
                    && ObjectHelper.isNotEmpty(configuration.getPassword());

            if (!hasAuthToken && !hasApiKey && !hasCredentials) {
                throw new IllegalArgumentException(
                        "Authentication required: provide either authToken, apiKey, or username/password");
            }
        }

        return endpoint;
    }

    public CyberArkVaultConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(CyberArkVaultConfiguration configuration) {
        this.configuration = configuration;
    }
}
