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
package org.apache.camel.component.avro;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The http consumer listens on the host of the endpoint, as the netty consumer does.
 */
public class AvroHttpConsumerHostTest extends AvroTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("avro:http:127.0.0.1:" + avroPort
                     + "/getName?protocolClassName=org.apache.camel.avro.test.TestReflection")
                        .setBody(constant("Camel"));
            }
        };
    }

    @Test
    public void testListensOnConfiguredHostOnly() throws Exception {
        InetAddress other = findNonLoopbackAddress();
        assumeTrue(other != null, "No non-loopback IPv4 address available");

        assertDoesNotThrow(() -> connect(InetAddress.getByName("127.0.0.1")));
        assertThrows(IOException.class, () -> connect(other));
    }

    private void connect(InetAddress address) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, avroPort), 2000);
        }
    }

    private static InetAddress findNonLoopbackAddress() throws SocketException {
        return NetworkInterface.networkInterfaces()
                .filter(nic -> {
                    try {
                        return nic.isUp() && !nic.isLoopback();
                    } catch (SocketException e) {
                        return false;
                    }
                })
                .flatMap(NetworkInterface::inetAddresses)
                .filter(address -> address instanceof Inet4Address && !address.isLoopbackAddress())
                .findFirst()
                .orElse(null);
    }
}
