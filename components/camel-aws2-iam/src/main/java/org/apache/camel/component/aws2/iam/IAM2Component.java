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
package org.apache.camel.component.aws2.iam;

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import software.amazon.awssdk.services.iam.IamClient;

/**
 * For working with Amazon IAM SDK v2.
 */
@Component("aws2-iam")
public class IAM2Component extends DefaultComponent {

    @Metadata
    private IAM2Configuration configuration = new IAM2Configuration();

    public IAM2Component() {
        this(null);
    }

    public IAM2Component(CamelContext context) {
        super(context);

        registerExtension(new IAM2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        IAM2Configuration configuration = this.configuration != null ? this.configuration.copy() : new IAM2Configuration();
        IAM2Endpoint endpoint = new IAM2Endpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (configuration.getIamClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("Amazon IAM client or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public IAM2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(IAM2Configuration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(IAM2Configuration configuration) {
        Set<IamClient> clients = getCamelContext().getRegistry().findByType(IamClient.class);
        if (clients.size() == 1) {
            configuration.setIamClient(clients.stream().findFirst().get());
        }
    }
}
