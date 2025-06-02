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

package org.apache.camel.test.infra.aws2.services;

import java.net.URI;
import java.util.Properties;

import org.apache.camel.test.infra.aws.common.AWSConfigs;
import org.apache.camel.test.infra.aws.common.AWSProperties;
import org.apache.camel.test.infra.aws.common.services.AWSInfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.regions.Region;

public abstract class AWSLocalContainerInfraService implements AWSInfraService, ContainerService<AWSContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(AWSLocalContainerInfraService.class);
    private final AWSContainer container;

    public AWSLocalContainerInfraService(Service... services) {
        this(LocalPropertyResolver.getProperty(AWSContainer.class, AWSProperties.AWS_CONTAINER), services);
    }

    public AWSLocalContainerInfraService(AWSContainer container) {
        this.container = container;
    }

    public AWSLocalContainerInfraService(String imageName, Service... services) {
        container = initContainer(imageName);

        container.setupServices(services);
    }

    protected AWSContainer initContainer(String imageName, Service... services) {
        return new AWSContainer(imageName, services);
    }

    private String getAmazonHost() {
        return container.getAmazonHost();
    }

    @Override
    public AWSContainer getContainer() {
        return container;
    }

    @Override
    public String amazonAWSHost() {
        return container.getAmazonHost();
    }

    @Override
    public String region() {
        return Region.US_EAST_1.toString();
    }

    @Override
    public String protocol() {
        return "http";
    }

    @Override
    public String accessKey() {
        return container.getCredentialsProvider().resolveCredentials().accessKeyId();
    }

    @Override
    public String secretKey() {
        return container.getCredentialsProvider().resolveCredentials().secretAccessKey();
    }

    @Override
    public Properties getConnectionProperties() {
        Properties properties = new Properties();

        properties.put(AWSConfigs.ACCESS_KEY, accessKey());
        properties.put(AWSConfigs.SECRET_KEY, secretKey());
        properties.put(AWSConfigs.REGION, region());
        properties.put(AWSConfigs.AMAZON_AWS_HOST, amazonAWSHost());
        properties.put(AWSConfigs.PROTOCOL, protocol());

        return properties;
    }

    public URI getServiceEndpoint() {
        return container.getServiceEndpoint();
    }

    @Override
    public void registerProperties() {
        AwsCredentials credentials = container.getCredentialsProvider().resolveCredentials();

        /**
         * We need to set these. For some sets, when they instantiate the clients within Camel, they need to know what
         * is the Amazon host being used (ie.: when creating them using the withEndpointConfiguration()). Because this
         * happens within Camel, there's no way to pass that information easily. Therefore, the host is set as a
         * property and read by whatever class/method creates the clients to pass to Camel.
         *
         * Do not unset.
         */
        System.setProperty(AWSConfigs.AMAZON_AWS_HOST, getAmazonHost());
        System.setProperty(AWSConfigs.SECRET_KEY, credentials.secretAccessKey());
        System.setProperty(AWSConfigs.ACCESS_KEY, credentials.accessKeyId());
        System.setProperty(AWSConfigs.AMAZON_AWS_HOST, getAmazonHost());
        System.setProperty(AWSConfigs.REGION, Region.US_EAST_1.toString());
        System.setProperty(AWSConfigs.PROTOCOL, "http");
    }

    @Override
    public void initialize() {
        LOG.debug("Trying to start the container");
        container.withStartupAttempts(5);
        container.start();

        registerProperties();
        LOG.info("AWS service running at address {}", getServiceEndpoint());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the local AWS services");
        container.stop();
    }
}
