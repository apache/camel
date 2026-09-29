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
package org.apache.camel.impl;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.StreamCachingStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The internal services that are kept when the CamelContext stops are started and stopped again when it is restarted.
 */
public class CamelContextRestartInternalServicesTest extends ContextTestSupport {

    private File spoolDir;

    @Test
    public void testRestart() throws Exception {
        StreamCachingStrategy strategy = context.getStreamCachingStrategy();
        assertTrue(spoolDir.exists(), "spool directory should be created");
        assertStatus(ServiceStatus.Started);

        context.stop();
        assertStatus(ServiceStatus.Stopped);
        assertFalse(spoolDir.exists(), "spool directory should be removed");

        context.start();
        assertSame(strategy, context.getStreamCachingStrategy());
        assertStatus(ServiceStatus.Started);
        assertTrue(spoolDir.exists(), "spool directory should be created again");

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello");
        template.sendBody("direct:start", "Hello");
        assertMockEndpointsSatisfied();

        context.stop();
        assertStatus(ServiceStatus.Stopped);
        assertFalse(spoolDir.exists(), "spool directory should be removed again");
    }

    private void assertStatus(ServiceStatus expected) {
        assertEquals(expected, status(context.getStreamCachingStrategy()), "stream caching strategy");
        assertEquals(expected, status(context.getInflightRepository()), "inflight repository");
        assertEquals(expected, status(context.getShutdownStrategy()), "shutdown strategy");
        assertEquals(expected, status(context.getPropertiesComponent()), "properties component");
        assertEquals(expected, status(context.getCamelContextExtension().getExchangeFactoryManager()),
                "exchange factory manager");
    }

    private static ServiceStatus status(Object service) {
        return ((StatefulService) service).getStatus();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                spoolDir = testDirectory("spool").toFile();
                context.getStreamCachingStrategy().setSpoolDirectory(spoolDir);
                context.getStreamCachingStrategy().setSpoolEnabled(true);
                context.getStreamCachingStrategy().setSpoolThreshold(1024);

                from("direct:start").streamCache("true").to("mock:result");
            }
        };
    }
}
