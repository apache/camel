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
package org.apache.camel.test.testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class ContainerAwareTestSupport extends CamelTestSupport {
    private List<GenericContainer<?>> containers = new CopyOnWriteArrayList<>();

    // ******************
    // Setup
    // ******************

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        containers.clear();
        containers.addAll(createContainers());

        final Network network = containerNetwork();
        final long timeout = containersStartupTimeout();

        Containers.start(containers, network, timeout);
    }

    @Override
    protected void cleanupResources() throws Exception {
        super.cleanupResources();

        Containers.stop(containers, containerShutdownTimeout());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        final PropertiesComponent pc = context.getPropertiesComponent();

        pc.addPropertiesFunction(new ContainerPropertiesFunction(containers));

        return context;
    }

    // ******************
    // Containers set-up
    // ******************

    protected GenericContainer<?> createContainer() {
        return null;
    }

    protected List<GenericContainer<?>> createContainers() {
        GenericContainer<?> container = createContainer();

        return container == null
            ? Collections.emptyList()
            : Collections.singletonList(container);
    }

    protected long containersStartupTimeout() {
        return TimeUnit.MINUTES.toSeconds(1);
    }

    protected long containerShutdownTimeout() {
        return TimeUnit.MINUTES.toSeconds(1);
    }

    protected Network containerNetwork() {
        return null;
    }

    // ******************
    // Helpers
    // ******************

    protected GenericContainer<?> getContainer(String containerName) {
        return Containers.lookup(containers, containerName);
    }

    protected String getContainerHost(String containerName) {
        return getContainer(containerName).getContainerIpAddress();
    }

    protected int getContainerPort(String containerName, int originalPort) {
        return getContainer(containerName).getMappedPort(originalPort);
    }
}
