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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The run form warns when the port of the app is taken already, and names the integration that holds it.
 */
class PortCheckTest {

    @Test
    void aTakenPortIsFoundWithItsOwner() throws Exception {
        try (ServerSocket taken = new ServerSocket()) {
            taken.bind(new InetSocketAddress(0));
            int port = taken.getLocalPort();
            assertThat(PortCheck.inUse(port)).isTrue();

            IntegrationInfo holder = new IntegrationInfo();
            holder.pid = "42";
            holder.name = "circuit-breaker";
            HttpEndpointInfo ep = new HttpEndpointInfo();
            ep.url = "http://0.0.0.0:" + port + "/hello";
            holder.httpEndpoints.add(ep);

            assertThat(PortCheck.warning(port, List.of(holder)))
                    .isEqualTo("Port " + port + " is taken by circuit-breaker; set another if this app serves HTTP");
            assertThat(PortCheck.warning(port, List.of())).contains("taken by another process");
        }
    }

    @Test
    void aFreePortHasNoWarning() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        assertThat(PortCheck.inUse(port)).isFalse();
        assertThat(PortCheck.warning(port, List.of())).isNull();
        assertThat(PortCheck.inUse(0)).isFalse();
    }
}
