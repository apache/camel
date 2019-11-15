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
package org.apache.camel.test.testcontainers.junit5;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import static java.util.stream.Collectors.joining;

public final class Containers {
    private static final Logger LOGGER = LoggerFactory.getLogger(Containers.class);

    private Containers() {
    }

    public static void start(List<GenericContainer<?>> containers, Network network, long timeout) throws Exception {
        final CountDownLatch latch = new CountDownLatch(containers.size());

        for (GenericContainer<?> container : containers) {
            if (ObjectHelper.isEmpty(container.getNetworkAliases())) {
                throw new IllegalStateException("Container should have at least a network alias");
            }

            if (network != null) {
                container.withNetwork(network);
            }

            // Add custom logger
            container.withLogConsumer(new Slf4jLogConsumer(LOGGER).withPrefix(container.getNetworkAliases().stream().collect(joining(","))));

            new Thread(() -> {
                container.start();
                latch.countDown();
            }).start();
        }

        latch.await(timeout, TimeUnit.SECONDS);
    }

    public static void stop(List<GenericContainer<?>> containers, long timeout) throws Exception {
        final CountDownLatch latch = new CountDownLatch(containers.size());

        for (GenericContainer<?> container : containers) {
            new Thread(() -> {
                container.stop();
                latch.countDown();
            }).start();
        }

        latch.await(timeout, TimeUnit.SECONDS);
    }

    public static GenericContainer<?> lookup(List<GenericContainer<?>> containers, String containerName) {
        for (GenericContainer<?> container : containers) {
            if (container.getNetworkAliases().contains(containerName)) {
                return container;
            }
        }

        throw new IllegalArgumentException("No container with name " + containerName + " found");
    }
}
