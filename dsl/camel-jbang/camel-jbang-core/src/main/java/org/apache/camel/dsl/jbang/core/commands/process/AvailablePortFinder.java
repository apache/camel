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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class AvailablePortFinder {

    private static final AvailablePortFinder INSTANCE = new AvailablePortFinder();

    private class Port implements AutoCloseable {
        final int port;

        public Port(int port) {
            this.port = port;
        }

        public int getPort() {
            return port;
        }

        public void release() {
            AvailablePortFinder.this.release(this);
        }

        public String toString() {
            return Integer.toString(port);
        }

        @Override
        public void close() {
            release();
        }
    }

    private final Map<Integer, Port> portMapping = new ConcurrentHashMap<>();

    synchronized void release(Port port) {
        INSTANCE.portMapping.remove(port.getPort(), port);
    }

    /**
     * Gets the next available port in the given range.
     *
     * @param  fromPort              port number start range.
     * @param  toPort                port number end range.
     *
     * @throws IllegalStateException if there are no ports available
     * @return                       the available port
     */
    public static int getNextAvailable(int fromPort, int toPort) {
        try (Port port = INSTANCE.findPort(fromPort, toPort)) {
            return port.getPort();
        }
    }

    synchronized Port findPort(int fromPort, int toPort) {
        for (int i = fromPort; i <= toPort; i++) {
            try {
                final int port = probePort(i);
                Port p = new Port(port);
                Port prv = INSTANCE.portMapping.putIfAbsent(port, p);
                if (prv == null) {
                    return p;
                }
            } catch (IllegalStateException e) {
                // do nothing, let's try the next port
            }
        }
        throw new IllegalStateException("Cannot find free port");
    }

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
    private static int probePort(int port) {
        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress((InetAddress) null, port), 1);
            return ss.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot find free port", e);
        }
    }

}
