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

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws.secretsmanager.client.SecretsManagerClientFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Manage secrets using AWS Secrets Manager.
 */
@UriEndpoint(firstVersion = "3.9.0", scheme = "aws-secrets-manager", title = "AWS Secrets Manager",
             syntax = "aws-secrets-manager:label", producerOnly = true, category = { Category.CLOUD, Category.MANAGEMENT },
             headersClass = SecretsManagerConstants.class)
@Metadata(annotations = {
        "vault=aws-secrets-manager",
})
public class SecretsManagerEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private SecretsManagerClient secretsManagerClient;

    @UriParam
    private SecretsManagerConfiguration configuration;

    public SecretsManagerEndpoint(String uri, Component component, SecretsManagerConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SecretsManagerProducer(this);
    }

    @Override
    public SecretsManagerComponent getComponent() {
        return (SecretsManagerComponent) super.getComponent();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        secretsManagerClient = configuration.getSecretsManagerClient() != null
                ? configuration.getSecretsManagerClient()
                : SecretsManagerClientFactory.getSecretsManagerClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getSecretsManagerClient())) {
            if (secretsManagerClient != null) {
                secretsManagerClient.close();
            }
        }
        super.doStop();
    }

    public SecretsManagerConfiguration getConfiguration() {
        return configuration;
    }

    public SecretsManagerClient getSecretsManagerClient() {
        return secretsManagerClient;
    }

    @Override
    public String getServiceUrl() {
        if (!configuration.isOverrideEndpoint()) {
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                return configuration.getRegion();
            }
        } else if (ObjectHelper.isNotEmpty(configuration.getUriEndpointOverride())) {
            return configuration.getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "secrets-manager";
    }
}
