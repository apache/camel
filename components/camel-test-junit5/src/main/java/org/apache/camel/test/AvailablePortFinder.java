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
package org.apache.camel.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds currently available server ports.
 */
public final class AvailablePortFinder {

    private static final Logger LOG = LoggerFactory.getLogger(AvailablePortFinder.class);

    /**
     * Creates a new instance.
     */
    private AvailablePortFinder() {
        // Do nothing
    }

    /**
     * Gets the next available port.
     *
     * @throws IllegalStateException if there are no ports available
     * @return the available port
     */
    public static int getNextAvailable() {
        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress((InetAddress) null, 0), 1);
            int port = ss.getLocalPort();
            LOG.info("getNextAvailable() -> {}", port);
            return port;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot find free port", e);
        }
    }
}
