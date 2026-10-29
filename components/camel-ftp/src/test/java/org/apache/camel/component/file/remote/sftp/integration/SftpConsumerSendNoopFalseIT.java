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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ftp.services.embedded.SftpEmbeddedService;
import org.apache.sshd.common.channel.RequestHandler;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With sendNoop=false the sftp consumer does not send a keep-alive request before each poll, and stays connected
 * between polls.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpConsumerSendNoopFalseIT extends BaseServerTestSupport {

    private final AtomicInteger keepAlives = new AtomicInteger();
    private final AtomicInteger logins = new AtomicInteger();

    @RegisterExtension
    protected SftpEmbeddedService service = new SftpEmbeddedService() {
        @Override
        public void setUpServer() throws Exception {
            super.setUpServer();
            // count the keep-alive requests and the logins the server sees
            List<RequestHandler<ConnectionService>> handlers = new ArrayList<>();
            handlers.add((connectionService, request, wantReply, buffer) -> {
                if (request.startsWith("keepalive@")) {
                    keepAlives.incrementAndGet();
                }
                // let the default handlers answer the request
                return RequestHandler.Result.Unsupported;
            });
            handlers.addAll(sshd.getGlobalRequestHandlers());
            sshd.setGlobalRequestHandlers(handlers);
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

    private String getSftpUrl() {
        return "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}/sendnoop?username=admin&password=admin"
               + "&knownHostsFile=" + service.getKnownHostsFile()
               + "&noop=true&delay=100&sendEmptyMessageWhenIdle=true";
    }

    @Test
    public void testSendNoopFalse() throws Exception {
        consumeFileAndPollAgain("sendNoopFalse");

        assertEquals(0, keepAlives.get(), "The consumer should not send a keep-alive before a poll when sendNoop=false");
        assertEquals(1, logins.get(), "The consumer should stay connected between polls");
    }

    @Test
    public void testSendNoopDefault() throws Exception {
        consumeFileAndPollAgain("sendNoopDefault");

        assertTrue(keepAlives.get() > 0, "The consumer should send a keep-alive before a poll by default");
        assertEquals(1, logins.get(), "The consumer should stay connected between polls");
    }

    private void consumeFileAndPollAgain(String routeId) throws Exception {
        template.sendBodyAndHeader("file://" + service.getFtpRootDir() + "/sendnoop", "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
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
