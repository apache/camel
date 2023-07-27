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
package org.apache.camel.test.infra.arangodb.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class ArangoDbContainer extends GenericContainer {
    public static final Integer PORT_DEFAULT = 8529;
    public static final String ARANGO_IMAGE = "arangodb:3.10.9";

    private static final Logger LOGGER = LoggerFactory.getLogger(ArangoDbContainer.class);
    private static final String CONTAINER_NAME = "arango";
    private static final String ARANGO_NO_AUTH = "ARANGO_NO_AUTH";

    public ArangoDbContainer() {
        this(ARANGO_IMAGE);
    }

    public ArangoDbContainer(String containerName) {
        super(containerName);

        setWaitStrategy(Wait.forListeningPort());
        addFixedExposedPort(PORT_DEFAULT, PORT_DEFAULT);
        withNetworkAliases(CONTAINER_NAME);
        withEnv(ARANGO_NO_AUTH, "1");
        withLogConsumer(new Slf4jLogConsumer(LOGGER));
        waitingFor(Wait.forLogMessage(".*is ready for business. Have fun!.*", 1));
    }

    public int getServicePort() {
        return getMappedPort(PORT_DEFAULT);
    }

}
