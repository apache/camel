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
package org.apache.camel.component.aws.parameterstore;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * For working with Amazon SSM Parameter Store SDK v2.
 */
@Component("aws-parameter-store")
public class ParameterStoreComponent extends HealthCheckComponent {

    @Metadata
    private ParameterStoreConfiguration configuration = new ParameterStoreConfiguration();

    public ParameterStoreComponent() {
        this(null);
    }

    public ParameterStoreComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ParameterStoreConfiguration configuration
                = ObjectHelper.isNotEmpty(this.configuration) ? this.configuration.copy() : new ParameterStoreConfiguration();
        ParameterStoreEndpoint endpoint = new ParameterStoreEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        if (Boolean.FALSE.equals(configuration.isUseDefaultCredentialsProvider())
                && Boolean.FALSE.equals(configuration.isUseProfileCredentialsProvider())
                && Boolean.FALSE.equals(configuration.isUseSessionCredentials())
                && configuration.getSsmClient() == null
                && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException(
                    "useDefaultCredentialsProvider is set to false, useProfileCredentialsProvider is set to false, useSessionCredentials is set to false, Amazon SSM client or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public ParameterStoreConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(ParameterStoreConfiguration configuration) {
        this.configuration = configuration;
    }
}
