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

import java.net.ServerSocket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AvailablePortFinderTest {

    @Test
    public void getNextAvailablePort() throws Exception {
        int p1 = AvailablePortFinder.getNextAvailable();
        int p2 = AvailablePortFinder.getNextAvailable();
        assertFalse(p1 == p2, "Port " + p1 + " Port2 " + p2);
    }

    @Test
    public void testGetNextAvailablePortInt() throws Exception {
        int p1 = AvailablePortFinder.getNextAvailable(9123);
        int p2 = AvailablePortFinder.getNextAvailable(9123);
        // these calls only check availability but don't mark the port as in
        // use.
        assertEquals(p1, p2);
    }

    @Test
    public void testNotAvailablePort() throws Exception {
        int p1 = AvailablePortFinder.getNextAvailable(11000);
        ServerSocket socket = new ServerSocket(p1);
        int p2 = AvailablePortFinder.getNextAvailable(p1);
        assertFalse(p1 == p2, "Port " + p1 + " Port2 " + p2);
        socket.close();
    }

    @Test
    public void getMinOutOfRangePort() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            AvailablePortFinder.getNextAvailable(AvailablePortFinder.MIN_PORT_NUMBER - 1);
        });
    }

    @Test
    public void getMaxOutOfRangePort() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            AvailablePortFinder.getNextAvailable(AvailablePortFinder.MAX_PORT_NUMBER + 1);
        });
    }

}
