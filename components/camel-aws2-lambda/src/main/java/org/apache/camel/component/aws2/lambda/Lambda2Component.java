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
import software.amazon.awssdk.services.lambda.LambdaClient;

/**
 * For working with Amazon Lambda SDK v2.
 */
@Component("aws2-lambda")
public class Lambda2Component extends DefaultComponent {

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
        checkAndSetRegistryClient(configuration);
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

    private void checkAndSetRegistryClient(Lambda2Configuration configuration) {
        Set<LambdaClient> clients = getCamelContext().getRegistry().findByType(LambdaClient.class);
        if (clients.size() == 1) {
            configuration.setAwsLambdaClient(clients.stream().findFirst().get());
        }
    }
}
