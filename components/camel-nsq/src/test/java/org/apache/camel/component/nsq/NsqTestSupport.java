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
package org.apache.camel.component.nsq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.test.testcontainers.ContainerAwareTestSupport;
import org.apache.camel.test.testcontainers.Wait;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class NsqTestSupport extends ContainerAwareTestSupport {

    public static final String CONTAINER_NSQLOOKUPD_IMAGE = "nsqio/nsq:v1.2.0";
    public static final String CONTAINER_NSQLOOKUPD_NAME = "nsqlookupd";

    public static final String CONTAINER_NSQD_IMAGE = "nsqio/nsq:v1.2.0";
    public static final String CONTAINER_NSQD_NAME = "nsqd";

    Network network;

    @Override
    protected List<GenericContainer<?>> createContainers() {
        network = Network.newNetwork();
        return new ArrayList<>(Arrays.asList(nsqlookupdContainer(network), nsqdContainer(network)));
    }

    public static GenericContainer<?> nsqlookupdContainer(Network network) {
        return new FixedHostPortGenericContainer<>(CONTAINER_NSQLOOKUPD_IMAGE).withFixedExposedPort(4160, 4160).withFixedExposedPort(4161, 4161)
            .withNetworkAliases(CONTAINER_NSQLOOKUPD_NAME).withCommand("/nsqlookupd").withNetwork(network).waitingFor(Wait.forLogMessageContaining("TCP: listening on", 1));
    }

    public static GenericContainer<?> nsqdContainer(Network network) {
        return new FixedHostPortGenericContainer<>(CONTAINER_NSQD_IMAGE).withFixedExposedPort(4150, 4150).withFixedExposedPort(4151, 4151).withNetworkAliases(CONTAINER_NSQD_NAME)
            .withCommand(String.format("/nsqd --broadcast-address=%s --lookupd-tcp-address=%s:4160", "localhost", CONTAINER_NSQLOOKUPD_NAME)).withNetwork(network).waitingFor(Wait.forLogMessageContaining("TCP: listening on", 1));
    }

    public String getNsqConsumerUrl() {
        return String.format("%s:%d", "localhost", 4161);
    }

    public String getNsqProducerUrl() {
        return String.format("%s:%d", "localhost", 4150);
    }
}
