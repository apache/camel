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
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds currently available server ports.
 */
public final class AvailablePortFinder {

    /**
     * The minimum server currentMinPort number for IPv4. Set at 1100 to avoid
     * returning privileged currentMinPort numbers.
     */
    public static final int MIN_PORT_NUMBER = 1100;

    /**
     * The maximum server currentMinPort number for IPv4.
     */
    public static final int MAX_PORT_NUMBER = 65535;

    private static final Logger LOG = LoggerFactory.getLogger(AvailablePortFinder.class);

    /**
     * We'll hold open the lowest port in this process so parallel processes
     * won't use the same block of ports. They'll go up to the next block.
     */
    private static final ServerSocket LOCK;

    /**
     * Incremented to the next lowest available port when getNextAvailable() is
     * called.
     */
    private static AtomicInteger currentMinPort = new AtomicInteger(MIN_PORT_NUMBER);

    /**
     * Creates a new instance.
     */
    private AvailablePortFinder() {
        // Do nothing
    }

    static {
        int port = MIN_PORT_NUMBER;
        ServerSocket serverSocker = null;

        while (serverSocker == null) {
            try {
                serverSocker = new ServerSocket(port);
            } catch (Exception e) {
                serverSocker = null;
                port += 200;
            }
        }
        LOCK = serverSocker;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    LOCK.close();
                } catch (Exception ex) {
                    // ignore
                }
            }
        });
        currentMinPort.set(port + 1);
    }

    /**
     * Gets the next available port starting at the lowest number. This is the
     * preferred method to use. The port return is immediately marked in use and
     * doesn't rely on the caller actually opening the port.
     *
     * @throws IllegalArgumentException is thrown if the port number is out of
     *             range
     * @throws NoSuchElementException if there are no ports available
     * @return the available port
     */
    public static synchronized int getNextAvailable() {
        int next = getNextAvailable(currentMinPort.get());
        currentMinPort.set(next + 1);
        return next;
    }

    /**
     * Gets the next available port starting at a given from port.
     *
     * @param fromPort the from port to scan for availability
     * @throws IllegalArgumentException is thrown if the port number is out of
     *             range
     * @throws NoSuchElementException if there are no ports available
     * @return the available port
     */
    public static synchronized int getNextAvailable(int fromPort) {
        if (fromPort < currentMinPort.get() || fromPort > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("From port number not in valid range: " + fromPort);
        }

        for (int currentPort = fromPort; currentPort <= MAX_PORT_NUMBER; currentPort++) {
            if (available(currentPort)) {
                LOG.info("getNextAvailable({}) -> {}", fromPort, currentPort);
                return currentPort;
            }
        }

        throw new NoSuchElementException("Could not find an available port above " + fromPort);
    }

    /**
     * Checks to see if a specific port is available.
     *
     * @param port the port number to check for availability
     * @return <tt>true</tt> if the port is available, or <tt>false</tt> if not
     * @throws IllegalArgumentException is thrown if the port number is out of
     *             range
     */
    public static boolean available(int port) throws IllegalArgumentException {
        if (port < currentMinPort.get() || port > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("Invalid start currentMinPort: " + port);
        }

        ServerSocket serverSocket = null;
        DatagramSocket datagramSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            datagramSocket = new DatagramSocket(port);
            datagramSocket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            // Do nothing
        } finally {
            if (datagramSocket != null) {
                datagramSocket.close();
            }

            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    /* should not be thrown */
                }
            }
        }

        return false;
    }

}
