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
package org.apache.camel.component.rabbitmq.integration;

import java.util.Arrays;

import org.apache.camel.test.testcontainers.Wait;
import org.testcontainers.containers.GenericContainer;

public class DockerTestUtils {
    public static final String CONTAINER_IMAGE = "rabbitmq:3-management";
    public static final String CONTAINER_NAME = "some-rabbit";
    public static final int EXPOSE_PORT_BROKER = 5672;
    public static final int EXPOSE_PORT_MANAGEMENT = 15672;

    protected DockerTestUtils() {
    }

    public static GenericContainer rabbitMQContainer() {
        // docker run -it -p 5672:5672 -p 15672:15672
        // -e RABBITMQ_DEFAULT_USER=cameltest
        // -e RABBITMQ_DEFAULT_PASS=cameltest
        // --hostname my-rabbit
        // --name some-rabbit rabbitmq:3-management
        GenericContainer container = new GenericContainer<>(CONTAINER_IMAGE).withNetworkAliases(CONTAINER_NAME).withExposedPorts(EXPOSE_PORT_BROKER, EXPOSE_PORT_MANAGEMENT)
            .withEnv("RABBITMQ_DEFAULT_USER", "cameltest").withEnv("RABBITMQ_DEFAULT_PASS", "cameltest").withCreateContainerCmdModifier(cmd -> cmd.withHostName("my-rabbit"))
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*\n", 1));
        container.setPortBindings(Arrays.asList(String.format("%d:%d", EXPOSE_PORT_BROKER, EXPOSE_PORT_BROKER), String.format("%d:%d", EXPOSE_PORT_MANAGEMENT, EXPOSE_PORT_MANAGEMENT)));

        return container;
    }
}
