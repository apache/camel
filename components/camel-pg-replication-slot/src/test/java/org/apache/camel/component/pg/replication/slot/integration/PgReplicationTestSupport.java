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

package org.apache.camel.component.pg.replication.slot.integration;

import org.apache.camel.test.testcontainers.junit5.ContainerAwareTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.TestcontainersConfiguration;

public class PgReplicationTestSupport extends ContainerAwareTestSupport {

    public static final String CONTAINER_NAME = "pg-replication-slot";

    private static final Logger LOG = LoggerFactory.getLogger(PgReplicationTestSupport.class);
    private static final int POSTGRES_PORT = 5432;
    private static final String POSTGRES_IMAGE = "postgres:13.0";

    @Override
    protected GenericContainer<?> createContainer() {
        LOG.info(TestcontainersConfiguration.getInstance().toString());

        GenericContainer<?> container = new GenericContainer(POSTGRES_IMAGE)
                .withCommand("postgres -c wal_level=logical")
                .withNetworkAliases(CONTAINER_NAME)
                .withExposedPorts(POSTGRES_PORT)
                .withEnv("POSTGRES_USER", "camel")
                .withEnv("POSTGRES_PASSWORD", "camel")
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .waitingFor(Wait.forListeningPort());

        return container;
    }

    public String getAuthority() {
        return String.format("%s:%s", getContainer(CONTAINER_NAME).getContainerIpAddress(),
                getContainer(CONTAINER_NAME).getMappedPort(POSTGRES_PORT));
    }

    public String getUser() {
        return "camel";
    }

    public String getPassword() {
        return "camel";
    }

}
