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

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Stopping the CamelContext must release the CoAP servers of the component: CoapServer.stop() keeps the executors of
 * the server, whose threads are not daemon threads, so a server that is only stopped (not destroyed) keeps the JVM
 * running after the CamelContext is stopped.
 */
class CoAPComponentStopDestroysServersTest {

    @RegisterExtension
    static AvailablePortFinder.Port port = CoAPTestPorts.findUdpPort();

    @Test
    void stoppingTheCamelContextReleasesTheServerThreads() throws Exception {
        Set<Thread> before = Thread.getAllStackTraces().keySet();
        CamelContext context = createContext();
        try {
            context.start();
            assertEquals("Hi", get());
        } finally {
            context.stop();
        }

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<String> left = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> !before.contains(t) && t.isAlive() && !t.isDaemon()
                            && t.getName().startsWith("CoapServer"))
                    .map(Thread::getName).sorted().toList();
            assertEquals(List.of(), left, "non-daemon CoapServer threads still alive after CamelContext.stop()");
        });
    }

    @Test
    void aCamelContextStartedAgainServesAgain() throws Exception {
        // control: the servers destroyed by the stop are created again by the consumers when the context starts again
        CamelContext context = createContext();
        try {
            context.start();
            assertEquals("Hi", get());
            context.stop();
            context.start();
            assertEquals("Hi", get());
        } finally {
            context.stop();
        }
    }

    private static CamelContext createContext() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("coap://localhost:%d/hello", port.getPort()).setBody(constant("Hi"));
            }
        });
        return context;
    }

    private static String get() throws Exception {
        CoapClient client = new CoapClient("coap://localhost:" + port.getPort() + "/hello");
        try {
            CoapResponse response = client.get();
            assertNotNull(response, "no response from the CoAP server");
            return response.getResponseText();
        } finally {
            client.shutdown();
        }
    }
}
