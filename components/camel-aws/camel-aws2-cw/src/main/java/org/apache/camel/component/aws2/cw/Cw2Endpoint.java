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
package org.apache.camel.component.aws2.cw;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.cw.client.Cw2ClientFactory;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.impl.health.ComponentsHealthCheckRepository;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

/**
 * Sending metrics to AWS CloudWatch using AWS SDK version 2.x.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-cw", title = "AWS CloudWatch", syntax = "aws2-cw:namespace",
             producerOnly = true, category = { Category.CLOUD, Category.MONITORING }, headersClass = Cw2Constants.class)
public class Cw2Endpoint extends DefaultEndpoint {

    @UriParam
    private Cw2Configuration configuration;
    private CloudWatchClient cloudWatchClient;
    private ComponentsHealthCheckRepository healthCheckRepository;
    private Cw2ClientHealthCheck clientHealthCheck;

    public Cw2Endpoint(String uri, Component component, Cw2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Cw2Producer(this);
    }

    @Override
    public void doInit() throws Exception {
        super.doInit();

        cloudWatchClient = configuration.getAmazonCwClient() != null
                ? configuration.getAmazonCwClient() : Cw2ClientFactory.getCloudWatchClient(configuration).getCloudWatchClient();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonCwClient())) {
            if (cloudWatchClient != null) {
                cloudWatchClient.close();
            }
        }
        super.doStop();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(getCamelContext(),
                ComponentsHealthCheckRepository.REPOSITORY_ID, ComponentsHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            // Do not register the health check until we resolve CAMEL-18992
            //clientHealthCheck = new Cw2ClientHealthCheck(this, getId());
            //healthCheckRepository.addHealthCheck(clientHealthCheck);
        }
    }

    public Cw2Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Cw2Configuration configuration) {
        this.configuration = configuration;
    }

    public void setCloudWatchClient(CloudWatchClient cloudWatchClient) {
        this.cloudWatchClient = cloudWatchClient;
    }

    public CloudWatchClient getCloudWatchClient() {
        return cloudWatchClient;
    }
}
