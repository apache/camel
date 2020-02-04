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
package org.apache.camel.component.pulsar;

import org.apache.camel.test.testcontainers.ContainerAwareTestSupport;
import org.apache.camel.test.testcontainers.Wait;
import org.testcontainers.containers.GenericContainer;

public class PulsarTestSupport extends ContainerAwareTestSupport {

    public static final String CONTAINER_IMAGE = "apachepulsar/pulsar:2.5.0";
    public static final String CONTAINER_NAME = "pulsar";
    public static final int BROKER_PORT = 6650;
    public static final int BROKER_HTTP_PORT = 8080;
    public static final String WAIT_FOR_ENDPOINT = "/admin/v2/namespaces/public";

    @Override
    protected GenericContainer<?> createContainer() {
        return pulsarContainer();
    }

    public static GenericContainer pulsarContainer() {
        return new GenericContainer(CONTAINER_IMAGE).withNetworkAliases(CONTAINER_NAME).withExposedPorts(BROKER_PORT, BROKER_HTTP_PORT)
            .withCommand("/pulsar/bin/pulsar", "standalone", "--no-functions-worker", "-nss")
            .waitingFor(Wait.forHttp(WAIT_FOR_ENDPOINT).forStatusCode(200).forPort(BROKER_HTTP_PORT));
    }

    public String getPulsarBrokerUrl() {
        return String.format("pulsar://%s:%s", getContainer(CONTAINER_NAME).getContainerIpAddress(), getContainer(CONTAINER_NAME).getMappedPort(BROKER_PORT));
    }
}
