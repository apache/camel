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

package org.apache.camel.test.infra.dispatch.router.services;

import org.apache.camel.test.infra.common.TestUtils;
import org.apache.camel.test.infra.messaging.services.MessagingContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class DispatchRouterContainer extends GenericContainer<DispatchRouterContainer> implements MessagingContainer {
    private static final int DEFAULT_AMQP_PORT = 5672;
    private static final String FROM_IMAGE_NAME = "fedora:38";
    private static final String FROM_IMAGE_ARG = "FROMIMAGE";

    public DispatchRouterContainer() {
        super(new ImageFromDockerfile("localhost/qpid-dispatch:camel", false)
                .withFileFromClasspath("Dockerfile",
                        "org/apache/camel/test/infra/dispatch/router/services/Dockerfile")
                .withBuildArg(FROM_IMAGE_ARG, TestUtils.prependHubImageNamePrefixIfNeeded(FROM_IMAGE_NAME)));

        withExposedPorts(DEFAULT_AMQP_PORT);

        waitingFor(Wait.forListeningPort());
    }

    /**
     * Gets the port number used for exchanging messages using the AMQP protocol
     *
     * @return the port number
     */
    public int getAMQPPort() {
        return getMappedPort(DEFAULT_AMQP_PORT);
    }

    /**
     * Gets the end point URL used exchanging messages using the AMQP protocol (ie.: tcp://host:${amqp.port})
     *
     * @return the end point URL as a string
     */
    public String getAMQPEndpoint() {
        return String.format("amqp://%s:%d", getHost(), getAMQPPort());
    }

    @Override
    public String defaultEndpoint() {
        return getAMQPEndpoint();
    }
}
