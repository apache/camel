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

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.iam.client.IAM2ClientFactory;
import org.apache.camel.component.aws2.iam.client.IAM2HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.impl.health.ComponentsHealthCheckRepository;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.iam.IamClient;

/**
 * Manage AWS IAM instances using AWS SDK version 2.x.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-iam", title = "AWS Identity and Access Management (IAM)",
             syntax = "aws2-iam:label", producerOnly = true, category = { Category.CLOUD, Category.MANAGEMENT },
             headersClass = IAM2Constants.class)
public class IAM2Endpoint extends ScheduledPollEndpoint {

    private IamClient iamClient;
    private ComponentsHealthCheckRepository healthCheckRepository;
    private IAM2HealthCheck clientHealthCheck;

    @UriParam
    private IAM2Configuration configuration;

    public IAM2Endpoint(String uri, Component component, IAM2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new IAM2Producer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        iamClient = configuration.getIamClient() != null
                ? configuration.getIamClient()
                : IAM2ClientFactory.getIamClient(configuration).getIamClient();
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(getCamelContext(),
                ComponentsHealthCheckRepository.REPOSITORY_ID, ComponentsHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            clientHealthCheck = new IAM2HealthCheck(this, getId());
        }

        healthCheckRepository.addHealthCheck(clientHealthCheck);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getIamClient())) {
            if (iamClient != null) {
                iamClient.close();
            }
        }
        if (healthCheckRepository != null && clientHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(clientHealthCheck);
            clientHealthCheck = null;
        }
        super.doStop();
    }

    public IAM2Configuration getConfiguration() {
        return configuration;
    }

    public IamClient getIamClient() {
        return iamClient;
    }
}
