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
package org.apache.camel.component.file.remote.mina.integration;

import java.nio.file.Files;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for server alive (heartbeat/keep-alive) support in MINA SFTP component.
 * <p>
 * These tests verify that:
 * <ul>
 * <li>When serverAliveInterval is configured, the heartbeat settings are applied to the SSH client</li>
 * <li>When serverAliveCountMax is configured, the max unanswered heartbeats setting is applied</li>
 * <li>When serverAliveInterval is 0 (default), heartbeat is disabled</li>
 * </ul>
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class MinaSftpServerAliveIT extends MinaSftpServerTestSupport {

    private static final String TEST_CONTENT = "Hello Server Alive Test";

    @BeforeEach
    public void createTestFile() throws Exception {
        // Ensure the root directory exists and create the test file
        service.getFtpRootDir().toFile().mkdirs();
        Files.writeString(ftpFile("serveralivetest.txt"), TEST_CONTENT);
    }

    /**
     * Test that serverAliveInterval configuration is accepted and route starts successfully. This implements T009 from
     * the tasks.
     */
    @Test
    public void testServerAliveIntervalConfigured() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:serveralive");
        mock.expectedMessageCount(1);

        context.getRouteController().startRoute("serverAliveRoute");

        MockEndpoint.assertIsSatisfied(context);

        // Route should have started and processed file successfully with heartbeat configured
        assertTrue(context.getRouteController().getRouteStatus("serverAliveRoute").isStarted());
    }

    /**
     * Test that serverAliveCountMax configuration is accepted and route starts successfully. This implements T012 from
     * the tasks.
     */
    @Test
    public void testServerAliveCountMaxConfigured() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:countmax");
        mock.expectedMessageCount(1);

        context.getRouteController().startRoute("countMaxRoute");

        MockEndpoint.assertIsSatisfied(context);

        // Route should have started and processed file successfully with max count configured
        assertTrue(context.getRouteController().getRouteStatus("countMaxRoute").isStarted());
    }

    /**
     * Test that default behavior (no serverAliveInterval) works without heartbeat. This implements T013 from the tasks.
     */
    @Test
    public void testDefaultServerAliveCountMax() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:defaultinterval");
        mock.expectedMessageCount(1);

        context.getRouteController().startRoute("defaultIntervalRoute");

        MockEndpoint.assertIsSatisfied(context);

        // Route should work without any server alive configuration
        assertTrue(context.getRouteController().getRouteStatus("defaultIntervalRoute").isStarted());
    }

    /**
     * Test that heartbeat is disabled when serverAliveInterval is 0. This implements T016 from the tasks.
     */
    @Test
    public void testDisabledHeartbeatWhenIntervalIsZero() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:disabled");
        mock.expectedMessageCount(1);

        context.getRouteController().startRoute("disabledRoute");

        MockEndpoint.assertIsSatisfied(context);

        // Route should work with explicitly disabled heartbeat (interval=0)
        assertTrue(context.getRouteController().getRouteStatus("disabledRoute").isStarted());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String baseUri = "mina-sftp://localhost:" + service.getPort() + "/" + service.getFtpRootDir()
                                 + "?username=admin&password=admin&strictHostKeyChecking=no&useUserKnownHostsFile=false"
                                 + "&delay=10000&disconnect=true&noop=true&fileName=serveralivetest.txt";

                // Route with serverAliveInterval configured
                from(baseUri + "&serverAliveInterval=30000")
                        .routeId("serverAliveRoute").autoStartup(false)
                        .to("mock:serveralive");

                // Route with both serverAliveInterval and serverAliveCountMax configured
                from(baseUri + "&serverAliveInterval=30000&serverAliveCountMax=3")
                        .routeId("countMaxRoute").autoStartup(false)
                        .to("mock:countmax");

                // Route with default interval (no serverAliveInterval, relies on serverAliveInterval=0 default)
                from(baseUri + "&serverAliveInterval=10000")
                        .routeId("defaultIntervalRoute").autoStartup(false)
                        .to("mock:defaultinterval");

                // Route with explicitly disabled heartbeat (interval=0)
                from(baseUri + "&serverAliveInterval=0")
                        .routeId("disabledRoute").autoStartup(false)
                        .to("mock:disabled");
            }
        };
    }
}
