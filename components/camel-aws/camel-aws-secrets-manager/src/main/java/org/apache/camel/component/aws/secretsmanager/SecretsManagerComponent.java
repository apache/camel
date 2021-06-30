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
package org.apache.camel.component.aws.secretsmanager;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * For working with Amazon Secrets Manager SDK v2.
 */
@Component("aws-secrets-manager")
public class SecretsManagerComponent extends DefaultComponent {

    @Metadata
    private SecretsManagerConfiguration configuration = new SecretsManagerConfiguration();

    public SecretsManagerComponent() {
        this(null);
    }

    public SecretsManagerComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SecretsManagerConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new SecretsManagerConfiguration();
        SecretsManagerEndpoint endpoint = new SecretsManagerEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        if (!configuration.isUseDefaultCredentialsProvider() && configuration.getSecretsManagerClient() == null
                && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException(
                    "useDefaultCredentialsProvider is set to false, Amazon Secrets Manager client or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public SecretsManagerConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(SecretsManagerConfiguration configuration) {
        this.configuration = configuration;
    }
}
