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
package org.apache.camel.component.aws2.lambda;

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.lambda.LambdaClient;

/**
 * For working with Amazon Lambda SDK v2.
 */
@Component("aws2-lambda")
public class Lambda2Component extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(Lambda2Component.class);

    @Metadata
    private Lambda2Configuration configuration = new Lambda2Configuration();

    public Lambda2Component() {
        this(null);
    }

    public Lambda2Component(CamelContext context) {
        super(context);

        registerExtension(new Lambda2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Lambda2Configuration configuration = this.configuration != null ? this.configuration.copy() : new Lambda2Configuration();
        Lambda2Endpoint endpoint = new Lambda2Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        endpoint.setFunction(remaining);
        checkAndSetRegistryClient(configuration, endpoint);
        if (configuration.getAwsLambdaClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("accessKey/secretKey or awsLambdaClient must be specified");
        }

        return endpoint;
    }

    public Lambda2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(Lambda2Configuration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(Lambda2Configuration configuration, Lambda2Endpoint endpoint) {
        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getAwsLambdaClient())) {
            LOG.debug("Looking for an LambdaClient instance in the registry");
            Set<LambdaClient> clients = getCamelContext().getRegistry().findByType(LambdaClient.class);
            if (clients.size() == 1) {
                LOG.debug("Found exactly one LambdaClient instance in the registry");
                configuration.setAwsLambdaClient(clients.stream().findFirst().get());
            } else {
                LOG.debug("No LambdaClient instance in the registry");
            }
        } else {
            LOG.debug("LambdaClient instance is already set at endpoint level: skipping the check in the registry");
        }
    }
}
