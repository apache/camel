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
package org.apache.camel.component.aws.iam;

import java.util.Map;
import java.util.Set;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * For working with Amazon IAM.
 */
@Component("aws-iam")
public class IAMComponent extends DefaultComponent {

    @Metadata
    private IAMConfiguration configuration = new IAMConfiguration();

    public IAMComponent() {
        this(null);
    }

    public IAMComponent(CamelContext context) {
        super(context);

        registerExtension(new IAMComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        IAMConfiguration configuration = this.configuration != null ? this.configuration.copy() : new IAMConfiguration();
        IAMEndpoint endpoint = new IAMEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (configuration.getIamClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("Amazon IAM client or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public IAMConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(IAMConfiguration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(IAMConfiguration configuration) {
        Set<AmazonIdentityManagement> clients = getCamelContext().getRegistry().findByType(AmazonIdentityManagement.class);
        if (clients.size() == 1) {
            configuration.setIamClient(clients.stream().findFirst().get());
        }
    }
}
