/**
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
package org.apache.camel.testng;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.NoSuchElementException;

/**
 * Finds currently available server ports.
 */
public final class AvailablePortFinder {

    /**
     * The minimum server currentMinPort number for IPv4.
     * Set at 1100 to avoid returning privileged currentMinPort numbers.
     */
    public static final int MIN_PORT_NUMBER = 1100;

    /**
     * The maximum server currentMinPort number for IPv4.
     */
    public static final int MAX_PORT_NUMBER = 65535;

    /**
     * Creates a new instance.
     */
    private AvailablePortFinder() {
        // Do nothing
    }

    /**
     * Gets the next available port starting at the lowest port number.
     *
     * @throws NoSuchElementException if there are no ports available
     */
    public static int getNextAvailable() {
        return getNextAvailable(MIN_PORT_NUMBER);
    }

    /**
     * Gets the next available port starting at a port.
     *
     * @param fromPort the port to scan for availability
     * @throws NoSuchElementException if there are no ports available
     */
    public static int getNextAvailable(int fromPort) {
        if (fromPort < MIN_PORT_NUMBER || fromPort > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("Invalid start port: " + fromPort);
        }

        for (int i = fromPort; i <= MAX_PORT_NUMBER; i++) {
            if (available(i)) {
                return i;
            }
        }

        throw new NoSuchElementException("Could not find an available port above " + fromPort);
    }

    /**
     * Checks to see if a specific port is available.
     *
     * @param port the port to check for availability
     */
    public static boolean available(int port) {
        if (port < MIN_PORT_NUMBER || port > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("Invalid start port: " + port);
        }

        ServerSocket ss = null;
        DatagramSocket ds = null;
        try {
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            ds = new DatagramSocket(port);
            ds.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            // Do nothing
        } finally {
            if (ds != null) {
                ds.close();
            }

            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                    /* should not be thrown */
                }
            }
        }

        return false;
    }
}
