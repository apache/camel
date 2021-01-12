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

package org.apache.camel.test.infra.aws.services;

import java.util.Properties;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.regions.Regions;
import org.apache.camel.test.infra.aws.common.AWSConfigs;
import org.apache.camel.test.infra.aws.common.services.AWSService;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;

abstract class AWSLocalContainerService implements AWSService, ContainerService<LocalStackContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(AWSLocalContainerService.class);
    private final LocalStackContainer container;

    public AWSLocalContainerService(LocalStackContainer.Service... services) {
        this.container = new LocalStackContainer().withServices(services);
    }

    protected abstract String getServiceEndpoint();

    @Override
    public void registerProperties() {
        AWSCredentials credentials = getCredentials();

        /**
         * We need to set this one. For some sets, when they instantiate the clients within Camel, they need to know
         * what is the Amazon details being used (ie.: when creating them using the withEndpointConfiguration()).
         * Because this happens within Camel, there's no way to pass that information easily. Therefore, the information
         * is set as a property and read by whatever class/method creates the clients to pass to Camel.
         *
         * Do not unset.
         */
        System.setProperty(AWSConfigs.SECRET_KEY, credentials.getAWSSecretKey());
        System.setProperty(AWSConfigs.ACCESS_KEY, credentials.getAWSAccessKeyId());
        System.setProperty(AWSConfigs.AMAZON_AWS_HOST, getAmazonHost());
        System.setProperty(AWSConfigs.REGION, Regions.US_EAST_1.name());
        System.setProperty(AWSConfigs.PROTOCOL, "http");
    }

    @Override
    public void initialize() {
        LOG.debug("Trying to start the container");
        container.start();

        registerProperties();
        LOG.info("AWS service running at address {}", getServiceEndpoint());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping local AWS service");
        container.stop();
    }

    private AWSCredentials getCredentials() {
        return container.getDefaultCredentialsProvider().getCredentials();
    }

    @Override
    public Properties getConnectionProperties() {
        Properties properties = new Properties();

        AWSCredentials credentials = getCredentials();

        properties.put(AWSConfigs.ACCESS_KEY, credentials.getAWSAccessKeyId());
        properties.put(AWSConfigs.SECRET_KEY, credentials.getAWSSecretKey());
        properties.put(AWSConfigs.REGION, Regions.US_EAST_1.name());
        properties.put(AWSConfigs.AMAZON_AWS_HOST, getAmazonHost());
        properties.put(AWSConfigs.PROTOCOL, "http");

        return properties;
    }

    @Override
    public LocalStackContainer getContainer() {
        return container;
    }

    protected String getAmazonHost(int port) {
        return String.format("%s:%d", container.getContainerIpAddress(), container.getMappedPort(port));
    }

    public String getAmazonHost() {
        final int edgePort = 4566;

        return getAmazonHost(edgePort);
    }

    protected String getServiceEndpoint(LocalStackContainer.Service service) {
        return container
                .getEndpointConfiguration(service)
                .getServiceEndpoint();
    }
}
