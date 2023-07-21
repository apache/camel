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
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.camel.test.infra.aws.common.AWSProperties;
import org.apache.camel.test.infra.aws2.common.TestAWSCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

/*
 The reason we are not using LocalStack containers here is because they bundle AWS SDK v1. They would
 be added to the classpath during the test and, potentially, cause errors or cause the code to not
 behave as in runtime.
 */
public class AWSContainer extends GenericContainer<AWSContainer> {

    public static final String LOCALSTACK_CONTAINER = "localstack/localstack:2.2.0";

    private static final Logger LOG = LoggerFactory.getLogger(AWSLocalContainerService.class);
    private static final int SERVICE_PORT = 4566;

    public AWSContainer() {
        this(System.getProperty(AWSProperties.AWS_CONTAINER, LOCALSTACK_CONTAINER));
    }

    public AWSContainer(String imageName) {
        super(imageName);
    }

    public AWSContainer(String imageName, Service... services) {
        super(imageName);

        setupServices(services);
        setupContainer();
    }

    @Deprecated
    protected AWSContainer(String imageName, String serviceList) {
        super(imageName);

        setupServices(serviceList);
        setupContainer();
    }

    public void setupServices(Service... services) {
        String serviceList = Arrays.stream(services)
                .map(Service::serviceName)
                .collect(Collectors.joining(","));

        setupServices(serviceList);

    }

    public void setupServices(String serviceList) {
        LOG.debug("Creating services {}", serviceList);
        withEnv("SERVICE", serviceList);
    }

    protected void setupContainer() {
        withExposedPorts(SERVICE_PORT);
        waitingFor(Wait.forLogMessage(".*Ready\\.\n", 1));
    }

    public AwsCredentialsProvider getCredentialsProvider() {
        return TestAWSCredentialsProvider.CONTAINER_LOCAL_DEFAULT_PROVIDER;
    }

    protected String getAmazonHost() {
        return getHost() + ":" + getMappedPort(SERVICE_PORT);
    }

    public URI getServiceEndpoint() {
        try {
            String address = String.format("http://%s:%d", getHost(), getMappedPort(SERVICE_PORT));
            LOG.debug("Running on service endpoint: {}", address);

            return new URI(address);
        } catch (URISyntaxException e) {
            throw new RuntimeException(String.format("Unable to determine the service endpoint: %s", e.getMessage()), e);
        }

    }
}
