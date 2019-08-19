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

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class AvailablePortFinderTest {

    @Test
    public void testNotAvailableTcpPort() throws Exception {
        int p1 = AvailablePortFinder.getNextAvailable();
        ServerSocket socket = new ServerSocket(p1);
        int p2 = AvailablePortFinder.getNextAvailable();
        assertFalse(p1 == p2, "Port " + p1 + " Port2 " + p2);
        socket.close();
    }

    @Test
    public void testNotAvailableUdpPort() throws Exception {
        int p1 = AvailablePortFinder.getNextAvailable();
        DatagramSocket socket = new DatagramSocket(p1);
        int p2 = AvailablePortFinder.getNextAvailable();
        assertFalse(p1 == p2, "Port " + p1 + " Port2 " + p2);
        socket.close();
    }

    @Test
    public void testNotAvailableMulticastPort() throws Exception {
        int p1 = AvailablePortFinder.getNextAvailable();
        MulticastSocket socket = new MulticastSocket(null);
        socket.setReuseAddress(false); // true is default for MulticastSocket, we wan to fail if port is occupied
        socket.bind(new InetSocketAddress(InetAddress.getLocalHost(), p1));
        int p2 = AvailablePortFinder.getNextAvailable();
        assertFalse(p1 == p2, "Port " + p1 + " Port2 " + p2);
        socket.close();
    }

}
