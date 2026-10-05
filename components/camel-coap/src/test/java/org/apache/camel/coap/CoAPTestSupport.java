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
package org.apache.camel.coap;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.elements.config.Configuration;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CoAPTestSupport extends CamelTestSupport {

    @RegisterExtension
    static AvailablePortFinder.Port PORT = findUdpPort();

    /**
     * Finds a port that is free for UDP as well as TCP. {@link AvailablePortFinder} only probes TCP, but CoAP over UDP
     * and DTLS binds a UDP socket on all interfaces, so a port that is free for TCP can still be taken for UDP.
     */
    static AvailablePortFinder.Port findUdpPort() {
        List<AvailablePortFinder.Port> rejected = new ArrayList<>();
        try {
            for (int i = 0; i < 100; i++) {
                AvailablePortFinder.Port port = AvailablePortFinder.find();
                if (isUdpPortFree(port.getPort())) {
                    return port;
                }
                // keep the rejected port reserved until we are done, so find() does not return it again
                rejected.add(port);
            }
            throw new IllegalStateException("Could not find a port that is free for both TCP and UDP");
        } finally {
            rejected.forEach(AvailablePortFinder.Port::release);
        }
    }

    private static boolean isUdpPortFree(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            return true;
        } catch (SocketException e) {
            return false;
        }
    }

    @Override
    public void doPostSetup() {
        Configuration.createStandardWithoutFile();
    }

    protected CoapClient createClient(String path) {
        return createClient(path, PORT.getPort());
    }

    protected CoapClient createClient(String path, int port) {
        String url = String.format("coap://localhost:%d/%s", port, FileUtil.stripLeadingSeparator(path));
        return new CoapClient(url);
    }
}
