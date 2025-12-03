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

public class AvailablePort {
    private static final Logger LOG = LoggerFactory.getLogger(AvailablePort.class);

    /**
     * Probe a port to see if it is free
     *
     * @param  port                  an integer port number to be tested. If port is 0, then the next available port is
     *                               returned.
     * @throws IllegalStateException if the port is not free or, in case of port 0, if there are no ports available at
     *                               all.
     * @return                       the port number itself if the port is free or, in case of port 0, the first
     *                               available port number.
     */
    public static int probePort(InetAddress address, int port) {

        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(address, port), 1);
            int probedPort = ss.getLocalPort();
            LOG.info("Available port is -> {}", probedPort);
            return probedPort;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot find free port", e);
        }
    }
}
