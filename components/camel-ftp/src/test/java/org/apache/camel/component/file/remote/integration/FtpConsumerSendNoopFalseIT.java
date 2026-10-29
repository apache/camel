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
package org.apache.camel.component.file.remote.integration;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultPollingConsumerPollStrategy;
import org.apache.camel.test.infra.ftp.services.embedded.EmbeddedConfiguration;
import org.apache.camel.test.infra.ftp.services.embedded.FtpEmbeddedService;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer counterpart of {@link FtpProducerSendNoopFalseIT}: with sendNoop=false the consumer does not send a NOOP
 * command before each poll, and stays logged in between polls, and it connects again after the server has closed the
 * connection, also with a poll strategy that does not disconnect.
 */
public class FtpConsumerSendNoopFalseIT extends BaseServerTestSupport {

    private final AtomicInteger noops = new AtomicInteger();
    private final AtomicInteger logins = new AtomicInteger();
    private final AtomicInteger retrieves = new AtomicInteger();
    private final AtomicInteger pwds = new AtomicInteger();

    // a poll strategy that does not disconnect when a poll fails, unlike the default one of the ftp consumer
    @BindToRegistry("myPollStrategy")
    private final DefaultPollingConsumerPollStrategy pollStrategy = new DefaultPollingConsumerPollStrategy();

    @RegisterExtension
    public FtpEmbeddedService service = new FtpEmbeddedService() {
        @Override
        protected FtpServerFactory createFtpServerFactory(EmbeddedConfiguration embeddedConfiguration) {
            FtpServerFactory factory = super.createFtpServerFactory(embeddedConfiguration);
            // count the NOOP commands and the logins the server sees
            factory.setFtplets(Map.of("counter", new DefaultFtplet() {
                @Override
                public FtpletResult beforeCommand(FtpSession session, FtpRequest request)
                        throws FtpException, IOException {
                    if ("NOOP".equalsIgnoreCase(request.getCommand())) {
                        noops.incrementAndGet();
                    } else if ("USER".equalsIgnoreCase(request.getCommand())) {
                        logins.incrementAndGet();
                    } else if ("RETR".equalsIgnoreCase(request.getCommand())) {
                        retrieves.incrementAndGet();
                    } else if ("PWD".equalsIgnoreCase(request.getCommand())) {
                        pwds.incrementAndGet();
                    }
                    return super.beforeCommand(session, request);
                }
            }));
            return factory;
        }
    };

    private String getFtpUrl() {
        // noop=true keeps the file where it is, so the consumer does not rename it (which sends a NOOP of its own)
        return "ftp://admin@localhost:{{ftp.server.port}}/sendnoop?password=admin&noop=true&delay=100"
               + "&sendEmptyMessageWhenIdle=true";
    }

    private String getRecoverUrl() {
        // without a listing (useList=false) and without changing directory (stepwise=false), a poll sends nothing to
        // the server before it retrieves the file
        return "ftp://admin@localhost:{{ftp.server.port}}/recover?password=admin&delete=true&delay=100"
               + "&useList=false&stepwise=false&fileName=hello.txt&sendNoop=false";
    }

    @Test
    public void testSendNoopFalse() throws Exception {
        consumeFileAndPollAgain("sendNoopFalse");

        assertEquals(0, noops.get(), "The consumer should not send a NOOP before a poll when sendNoop=false");
        assertEquals(1, logins.get(), "The consumer should stay logged in between polls");
    }

    @Test
    public void testSendNoopDefault() throws Exception {
        consumeFileAndPollAgain("sendNoopDefault");

        assertTrue(noops.get() > 0, "The consumer should send a NOOP before a poll by default");
        assertEquals(1, logins.get(), "The consumer should stay logged in between polls");
    }

    @Test
    public void testSendNoopFalseConnectsAgainAfterServerClosedConnection() throws Exception {
        consumeAfterServerClosedConnection("recover");
    }

    @Test
    public void testSendNoopFalseConnectsAgainAfterServerClosedConnectionIgnoringFileNotFound() throws Exception {
        consumeAfterServerClosedConnection("recoverIgnoreFileNotFound");
    }

    private void consumeAfterServerClosedConnection(String routeId) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("one");

        template.sendBodyAndHeader("file://" + service.getFtpRootDir() + "/recover", "one", Exchange.FILE_NAME,
                "hello.txt");
        context.getRouteController().startRoute(routeId);

        MockEndpoint.assertIsSatisfied(context);
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> !service.ftpFile("recover/hello.txt").toFile().exists());
        // and the consumer has polled again for the deleted file, which the server answers with 550 (file not found)
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> retrieves.get() > 2);

        // the server closes the connection between polls, as after an idle timeout. The client does not notice, and
        // without a listing or a change of directory the closed connection is first found when retrieving the file
        // fails while processing it, and not during the poll
        service.disconnectAllSessions();

        mock.reset();
        mock.expectedBodiesReceived("two");

        template.sendBodyAndHeader("file://" + service.getFtpRootDir() + "/recover", "two", Exchange.FILE_NAME,
                "hello.txt");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(2, logins.get(), "The consumer should connect again after the server closed the connection");
    }

    @Test
    public void testSendNoopFalseConnectsAgainAfterServerClosedConnectionWithCustomPollStrategy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("one");

        template.sendBodyAndHeader("file://" + service.getFtpRootDir() + "/pollstrategy", "one", Exchange.FILE_NAME,
                "hello.txt");
        context.getRouteController().startRoute("recoverPollStrategy");

        MockEndpoint.assertIsSatisfied(context);
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> !service.ftpFile("pollstrategy/hello.txt").toFile().exists());
        // and the consumer has polled again, as each poll starts with a PWD command (stepwise=true)
        int pwdsAfterDelete = pwds.get();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> pwds.get() > pwdsAfterDelete + 1);

        // the server closes the connection between polls. The client does not notice, so the first command of the next
        // poll fails, and the failure is thrown out of the poll to a poll strategy that does not disconnect
        service.disconnectAllSessions();

        mock.reset();
        mock.expectedBodiesReceived("two");

        template.sendBodyAndHeader("file://" + service.getFtpRootDir() + "/pollstrategy", "two", Exchange.FILE_NAME,
                "hello.txt");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(2, logins.get(), "The consumer should connect again after the server closed the connection");
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
                from(getFtpUrl() + "&sendNoop=false").routeId("sendNoopFalse").autoStartup(false)
                        .to("mock:result");

                from(getFtpUrl()).routeId("sendNoopDefault").autoStartup(false)
                        .to("mock:result");

                from(getRecoverUrl()).routeId("recover").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:result");

                from(getRecoverUrl() + "&ignoreFileNotFoundOrPermissionError=true")
                        .routeId("recoverIgnoreFileNotFound").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:result");

                from("ftp://admin@localhost:{{ftp.server.port}}/pollstrategy?password=admin&delete=true&delay=100"
                     + "&sendNoop=false&pollStrategy=#myPollStrategy")
                        .routeId("recoverPollStrategy").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
