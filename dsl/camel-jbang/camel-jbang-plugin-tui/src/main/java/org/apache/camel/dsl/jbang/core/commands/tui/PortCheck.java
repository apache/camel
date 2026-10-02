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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.List;

/**
 * Whether the HTTP port of an app about to be run is taken already, and by whom: a Spring Boot app then fails with
 * APPLICATION FAILED TO START, and a Camel app cannot start its HTTP server.
 */
final class PortCheck {

    /** The port an app listens on when none is given (Camel, Spring Boot and Quarkus alike). */
    static final int DEFAULT_PORT = 8080;

    private PortCheck() {
    }

    /** Whether something listens on the port already. */
    static boolean inUse(int port) {
        if (port <= 0) {
            return false;
        }
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(port));
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    /** The integration that serves HTTP on the port, from what the TUI knows of the running ones; null if none. */
    static String owner(int port, List<IntegrationInfo> integrations) {
        for (IntegrationInfo info : integrations) {
            if (info.vanishing || info.phantom) {
                continue;
            }
            for (HttpEndpointInfo ep : info.httpEndpoints) {
                if (ep.url != null && portOf(ep.url) == port) {
                    return info.name != null ? info.name : info.pid;
                }
            }
        }
        return null;
    }

    /** The warning for the run form, or null when the port is free. */
    static String warning(int port, List<IntegrationInfo> integrations) {
        if (!inUse(port)) {
            return null;
        }
        String owner = owner(port, integrations);
        return "Port " + port + " is taken by " + (owner != null ? owner : "another process")
               + "; set another if this app serves HTTP";
    }

    private static int portOf(String url) {
        try {
            return URI.create(url).getPort();
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }
}
