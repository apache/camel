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
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds currently available server ports.
 */
public final class AvailablePortFinder {

    private static final Logger LOG = LoggerFactory.getLogger(AvailablePortFinder.class);

    private static final AvailablePortFinder INSTANCE = new AvailablePortFinder();

    public class Port implements BeforeEachCallback, AfterAllCallback, AutoCloseable {
        final int port;
        String testClass;
        Throwable creation;

        public Port(int port) {
            this.port = port;
            this.creation = new Throwable();
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

        public void beforeEach(ExtensionContext context) throws Exception {
            testClass = context.getTestClass().map(Class::getName).orElse(null);
            LOG.info("Registering port {} for test {}", port, testClass);
        }

        public void afterAll(ExtensionContext context) throws Exception {
            release();
        }

        @Override
        public void close() {
            release();
        }
    }

    private final Map<Integer, Port> portMapping = new ConcurrentHashMap<>();

    /**
     * Creates a new instance.
     */
    private AvailablePortFinder() {
        // Do nothing
    }

    public static Port find() {
        return INSTANCE.findPort();
    }

    synchronized Port findPort() {
        while (true) {
            final int port = probePort(0);
            Port p = new Port(port);
            Port prv = INSTANCE.portMapping.putIfAbsent(port, p);
            if (prv == null) {
                return p;
            }
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

    synchronized void release(Port port) {
        INSTANCE.portMapping.remove(port.getPort(), port);
    }

    /**
     * Gets the next available port.
     *
     * @throws IllegalStateException if there are no ports available
     * @return                       the available port
     */
    public static int getNextAvailable() {
        try (Port port = INSTANCE.findPort()) {
            return port.getPort();
        }
    }

    /**
     * Gets the next available port.
     *
     * @throws IllegalStateException if there are no ports available
     * @return                       the available port
     */
    public static int getNextRandomAvailable() {
        Random random = new Random();
        int fromPort = random.nextInt(10000, 65500);
        int toPort = random.nextInt(fromPort, 65500);
        try (Port port = INSTANCE.findPort(fromPort, toPort)) {
            return port.getPort();
        }
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

    /**
     * Gets the next available port in the given range.
     *
     * @param  portNumber            port number start range.
     * @param  failurePayload        handover data in case port allocation fails (i.e.: a default one to use)
     * @param  failureHandler        a handler in case the requested port is not available
     *
     * @throws IllegalStateException if there are no ports available
     * @return                       the available port
     */
    public static <T> int getSpecificPort(int portNumber, T failurePayload, Function<T, Integer> failureHandler) {
        try (Port port = INSTANCE.findPort(portNumber, portNumber)) {
            return port.getPort();
        } catch (IllegalStateException e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Unable to obtain the requested TCP port {}: {}", portNumber, e.getMessage(), e);
            } else {
                LOG.warn("Unable to obtain the requested TCP port {}: {}", portNumber, e.getMessage());
            }

            return failureHandler.apply(failurePayload);
        }
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
    public static int probePort(int port) {
        return probePort(null, port);
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
