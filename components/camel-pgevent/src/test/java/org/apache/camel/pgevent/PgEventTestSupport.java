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
package org.apache.camel.pgevent;

import org.apache.camel.test.testcontainers.junit5.ContainerAwareTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.TestcontainersConfiguration;

public class PgEventTestSupport extends ContainerAwareTestSupport {

    public static final String CONTAINER_NAME = "pg-event";
    protected static final String TEST_MESSAGE_BODY = "Test Camel PGEvent";
    protected static final String POSTGRES_USER = "postgres";
    protected static final String POSTGRES_PASSWORD = "mysecretpassword";
    protected static final String POSTGRES_DB = "postgres";

    private static final Logger LOG = LoggerFactory.getLogger(PgEventTestSupport.class);
    private static final int POSTGRES_PORT = 5432;
    private static final String POSTGRES_IMAGE = "postgres:13.0";

    protected GenericContainer container;

    @BeforeEach
    public void beforeEach() {
        container = createContainer();
        container.start();
    }

    @AfterEach
    public void afterEach() {
        if (container != null) {
            container.stop();
        }
    }

    @Override
    protected GenericContainer<?> createContainer() {
        LOG.info(TestcontainersConfiguration.getInstance().toString());

        GenericContainer<?> container = new GenericContainer(POSTGRES_IMAGE)
                .withCommand("postgres -c wal_level=logical")
                .withExposedPorts(POSTGRES_PORT)
                .withNetworkAliases(CONTAINER_NAME)
                .withEnv("POSTGRES_USER", POSTGRES_USER)
                .withEnv("POSTGRES_PASSWORD", POSTGRES_PASSWORD)
                .withEnv("POSTGRES_DB", POSTGRES_DB)
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .waitingFor(Wait.forListeningPort());
        return container;
    }

    public String getAuthority() {
        return String.format("%s:%s", getContainer(CONTAINER_NAME).getContainerIpAddress(),
                getContainer(CONTAINER_NAME).getMappedPort(POSTGRES_PORT));
    }

    public Integer getMappedPort() {
        return getContainer(CONTAINER_NAME).getMappedPort(POSTGRES_PORT);
    }

    public String getHost() {
        return getContainer(CONTAINER_NAME).getHost();
    }

}
