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
package org.apache.camel.component.couchdb;

import org.apache.camel.test.testcontainers.junit5.ContainerAwareTestSupport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class CouchDbTestSupport extends ContainerAwareTestSupport {

    public static final String CONTAINER_IMAGE = "couchdb:2.3.1"; // tested against 2.1.2, 2.2.0 & 2.3.1
    public static final String CONTAINER_NAME = "couchdb";
    public static final int BROKER_PORT = 5984;

    @Override
    protected GenericContainer<?> createContainer() {
        return new GenericContainer<>(CONTAINER_IMAGE).
            withNetworkAliases(CONTAINER_NAME).
            withExposedPorts(BROKER_PORT).
            waitingFor(Wait.forListeningPort());
    }

    public String getCouchDbHost() {
        return getContainer(CONTAINER_NAME).getContainerIpAddress();
    }

    public int getCouchDbPort() {
        return getContainer(CONTAINER_NAME).getMappedPort(BROKER_PORT).intValue();
    }

}
