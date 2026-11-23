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
package org.apache.camel.component.file.remote.sftp.integration;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ftp.services.embedded.SftpEmbeddedService;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.sftp.common.SftpConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that sendNoop controls whether a keep-alive REALPATH round-trip is sent before each poll.
 * <p>
 * Both sendNoop=true and sendNoop=false cause REALPATH requests (JSch resolves "." before every {@code ls}), but
 * sendNoop=true adds an extra REALPATH per poll for the noop check. The test runs both modes in sequence against the
 * same embedded server and asserts that the sendNoop=true run produces strictly more REALPATH requests than the
 * sendNoop=false baseline.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpConsumerSendNoopFalseIT extends BaseServerTestSupport {

    private final AtomicInteger logins = new AtomicInteger();

    @RegisterExtension
    protected SftpEmbeddedService service = new SftpExistsFileIT.CountingSftpEmbeddedService() {
        @Override
        public void setUpServer() throws Exception {
            super.setUpServer();
            // count the logins the server sees
            sshd.addSessionListener(new SessionListener() {
                @Override
                public void sessionEvent(Session session, Event event) {
                    if (event == Event.Authenticated) {
                        logins.incrementAndGet();
                    }
                }
            });
        }
    };

    private SftpExistsFileIT.CountingSftpEmbeddedService countingService() {
        return (SftpExistsFileIT.CountingSftpEmbeddedService) service;
    }

    private String getSftpUrl() {
        return "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}/sendnoop?username=admin&password=admin"
               + "&knownHostsFile=" + service.getKnownHostsFile()
               + "&noop=true&delay=100&sendEmptyMessageWhenIdle=true";
    }

    @Test
    public void testSendNoopDifference() throws Exception {
        // --- Run with sendNoop=false (baseline) ---
        consumeFileAndPollAgain("sendNoopFalse");
        // Stop the route before reading the counter: the consumer polls every 100 ms and may
        // still be in flight. Reading the counter while the route is running risks a race where
        // sendNoop=false accumulates extra REALPATH requests from JSch's internal cd/ls calls
        // between assertIsSatisfied() returning and the route actually stopping.
        context.getRouteController().stopRoute("sendNoopFalse");
        int realpathWithNoopFalse = countingService().requests(SftpConstants.SSH_FXP_REALPATH);
        assertEquals(1, logins.get(), "The consumer should stay connected between polls (sendNoop=false)");

        // reset counters and login count for the second run
        countingService().reset();
        logins.set(0);

        // --- Run with sendNoop=true (default) ---
        consumeFileAndPollAgain("sendNoopDefault");
        // Stop the route before reading, same reason as for the sendNoop=false run above.
        context.getRouteController().stopRoute("sendNoopDefault");
        int realpathWithNoopTrue = countingService().requests(SftpConstants.SSH_FXP_REALPATH);
        assertEquals(1, logins.get(), "The consumer should stay connected between polls (sendNoop=true)");

        // sendNoop=true sends an extra REALPATH round-trip before every poll, so it must produce strictly more
        assertTrue(realpathWithNoopTrue > realpathWithNoopFalse,
                "sendNoop=true should send more REALPATH requests (" + realpathWithNoopTrue
                                                                 + ") than sendNoop=false ("
                                                                 + realpathWithNoopFalse + ")");
    }

    private void consumeFileAndPollAgain(String routeId) throws Exception {
        template.sendBodyAndHeader("file://" + service.getFtpRootDir() + "/sendnoop", "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        // the file, and then an empty message for each poll that finds no new file
        mock.expectedMinimumMessageCount(5);
        mock.message(0).body(String.class).isEqualTo("Hello World");

        context.getRouteController().startRoute(routeId);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getSftpUrl() + "&sendNoop=false").routeId("sendNoopFalse").autoStartup(false)
                        .to("mock:result");

                from(getSftpUrl()).routeId("sendNoopDefault").autoStartup(false)
                        .to("mock:result");
            }
        };
    }
}
