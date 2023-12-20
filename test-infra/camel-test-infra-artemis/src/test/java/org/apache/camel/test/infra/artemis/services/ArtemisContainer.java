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
package org.apache.camel.test.infra.artemis.services;

import org.apache.camel.test.infra.common.TestUtils;
import org.apache.camel.test.infra.messaging.services.MessagingContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class ArtemisContainer extends GenericContainer<ArtemisContainer> implements MessagingContainer {
    private static final int DEFAULT_MQTT_PORT = 1883;
    private static final int DEFAULT_AMQP_PORT = 5672;
    private static final int DEFAULT_ADMIN_PORT = 8161;
    private static final int DEFAULT_ACCEPTOR_PORT = 61616;
    private static final String FROM_IMAGE_NAME = "fedora:38";
    private static final String FROM_IMAGE_ARG = "FROMIMAGE";

    public ArtemisContainer() {
        super(new ImageFromDockerfile("localhost/apache-artemis:camel", false)
                .withFileFromClasspath("Dockerfile",
                        "org/apache/camel/test/infra/artemis/services/Dockerfile")
                .withBuildArg(FROM_IMAGE_ARG, TestUtils.prependHubImageNamePrefixIfNeeded(FROM_IMAGE_NAME)));

        withExposedPorts(DEFAULT_MQTT_PORT, DEFAULT_AMQP_PORT,
                DEFAULT_ADMIN_PORT, DEFAULT_ACCEPTOR_PORT);

        waitingFor(Wait.forListeningPort());
    }

    /**
     * Gets the port number used for exchanging messages using the AMQP protocol
     *
     * @return the port number
     */
    public int amqpPort() {
        return getMappedPort(DEFAULT_AMQP_PORT);
    }

    /**
     * Gets the end point URL used exchanging messages using the AMQP protocol (ie.: tcp://host:${amqp.port})
     *
     * @return the end point URL as a string
     */
    public String amqpEndpoint() {
        return String.format("amqp://%s:%d", getHost(), amqpPort());
    }

    /**
     * Gets the port number used for exchanging messages using the MQTT protocol
     *
     * @return the port number
     */
    public int mqttPort() {
        return getMappedPort(DEFAULT_MQTT_PORT);
    }

    /**
     * Gets the end point URL used exchanging messages using the MQTT protocol (ie.: tcp://host:${mqtt.port})
     *
     * @return the end point URL as a string
     */
    public String mqttEndpoint() {
        return String.format("tcp://%s:%d", getHost(), mqttPort());
    }

    /**
     * Gets the port number used for accessing the web management console or the management API
     *
     * @return the port number
     */
    public int adminPort() {
        return getMappedPort(DEFAULT_ADMIN_PORT);
    }

    /**
     * Gets the end point URL used for accessing the web management console or the management API
     *
     * @return the admin URL as a string
     */
    public String adminURL() {
        return String.format("http://%s:%d", getHost(), adminPort());
    }

    /**
     * Gets the port number used for exchanging messages using the default acceptor port
     *
     * @return the port number
     */
    public int defaultAcceptorPort() {
        return getMappedPort(DEFAULT_ACCEPTOR_PORT);
    }

    /**
     * Gets the end point URL used exchanging messages through the default acceptor port
     *
     * @return the end point URL as a string
     */
    public String defaultEndpoint() {
        return String.format("tcp://%s:%d", getHost(), defaultAcceptorPort());
    }

    /**
     * Gets the port number used for exchanging messages using the Openwire protocol
     *
     * @return the port number
     */
    public int openwirePort() {
        return defaultAcceptorPort();
    }

    /**
     * Gets the end point URL used exchanging messages using the Openwire protocol (ie.: tcp://host:${amqp.port})
     *
     * @return the end point URL as a string
     */
    public String getOpenwireEndpoint() {
        return String.format("tcp://%s:%d", getHost(), openwirePort());
    }
}
