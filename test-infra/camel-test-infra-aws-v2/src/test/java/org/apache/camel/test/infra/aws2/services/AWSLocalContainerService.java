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
import org.apache.camel.test.infra.aws.common.services.AWSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.regions.Region;

abstract class AWSLocalContainerService<T> implements AWSService<T> {
    private static final Logger LOG = LoggerFactory.getLogger(AWSLocalContainerService.class);
    private AWSContainer container;

    public AWSLocalContainerService(Service... services) {
        container = new AWSContainer(services);
    }

    public AWSLocalContainerService(String containerName, Service... services) {
        container = new AWSContainer(containerName, services);
    }

    private String getAmazonHost() {
        return container.getAmazonHost();
    }

    protected AWSContainer getContainer() {
        return container;
    }

    @Override
    public Properties getConnectionProperties() {
        Properties properties = new Properties();

        AwsCredentials credentials = container.getCredentialsProvider().resolveCredentials();

        properties.put(AWSConfigs.ACCESS_KEY, credentials.accessKeyId());

        properties.put(AWSConfigs.SECRET_KEY, credentials.secretAccessKey());

        properties.put(AWSConfigs.REGION, Region.US_EAST_1.toString());

        properties.put(AWSConfigs.AMAZON_AWS_HOST, container.getAmazonHost());

        /**
         * We need to set this one. For some sets, when they instantiate the clients within Camel, they need to know
         * what is the Amazon host being used (ie.: when creating them using the withEndpointConfiguration()). Because
         * this happens within Camel, there's no way to pass that information easily. Therefore, the host is set as a
         * property and read by whatever class/method creates the clients to pass to Camel.
         *
         * Do not unset.
         */
        System.setProperty(AWSConfigs.AMAZON_AWS_HOST, getAmazonHost());

        properties.put(AWSConfigs.PROTOCOL, "http");

        return properties;
    }

    public URI getServiceEndpoint() {
        return container.getServiceEndpoint();
    }

    @Override
    public void initialize() {
        LOG.info("AWS service running at address {}", getServiceEndpoint());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the local AWS services");
        container.stop();
    }
}
