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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ftp.services.embedded.EmbeddedConfiguration;
import org.apache.camel.test.infra.ftp.services.embedded.FtpEmbeddedService;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ftp operations send a NOOP before a rename or delete only when sendNoop is on (the default), as the consumer does
 * before each poll and the producer before each upload. With sendNoop=false no NOOP is sent at all: not before the
 * consumer deletes or moves a processed file, nor before the producer renames its temporary file.
 */
public class FtpRenameDeleteSendNoopIT extends BaseServerTestSupport {

    private final List<String> commands = new CopyOnWriteArrayList<>();

    @RegisterExtension
    public FtpEmbeddedService service = new FtpEmbeddedService() {
        @Override
        protected FtpServerFactory createFtpServerFactory(EmbeddedConfiguration embeddedConfiguration) {
            FtpServerFactory factory = super.createFtpServerFactory(embeddedConfiguration);
            // record the commands the server sees, in their order
            factory.setFtplets(Map.of("recorder", new DefaultFtplet() {
                @Override
                public FtpletResult beforeCommand(FtpSession session, FtpRequest request)
                        throws FtpException, IOException {
                    commands.add(request.getCommand().toUpperCase(Locale.ROOT));
                    return super.beforeCommand(session, request);
                }
            }));
            return factory;
        }
    };

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testConsumerDelete(boolean sendNoop) throws Exception {
        consume("delete", sendNoop);
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .until(() -> !service.ftpFile("delete/hello.txt").toFile().exists());

        assertNoopBetween("RETR", "DELE", sendNoop);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testConsumerMove(boolean sendNoop) throws Exception {
        consume("move", sendNoop);
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .until(() -> service.ftpFile("move/done/hello.txt").toFile().exists());

        assertNoopBetween("RETR", "RNFR", sendNoop);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testProducerTempFileName(boolean sendNoop) {
        String uri = "ftp://admin@localhost:{{ftp.server.port}}/upload?password=admin&tempFileName=${file:name}.tmp"
                     + (sendNoop ? "" : "&sendNoop=false");
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertTrue(service.ftpFile("upload/hello.txt").toFile().exists(), "The file should be uploaded");
        assertNoopBetween("STOR", "RNFR", sendNoop);
    }

    private void consume(String routeId, boolean sendNoop) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file://" + service.getFtpRootDir() + "/" + routeId, "Hello World",
                Exchange.FILE_NAME, "hello.txt");
        context.getRouteController().startRoute(routeId + (sendNoop ? "Default" : "False"));

        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Asserts whether a NOOP is sent after the transfer of the file and before it is renamed or deleted, and with
     * sendNoop=false that no NOOP is sent at all.
     */
    private void assertNoopBetween(String transfer, String renameOrDelete, boolean sendNoop) {
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> commands.contains(renameOrDelete));

        List<String> recorded = new ArrayList<>(commands);
        int end = recorded.indexOf(renameOrDelete);
        int start = recorded.subList(0, end).lastIndexOf(transfer);
        assertTrue(start >= 0, "The file should be transferred before it is renamed or deleted: " + recorded);
        List<String> between = recorded.subList(start + 1, end);

        if (sendNoop) {
            assertTrue(between.contains("NOOP"),
                    "A NOOP should be sent before " + renameOrDelete + " by default: " + recorded);
        } else {
            assertFalse(between.contains("NOOP"),
                    "No NOOP should be sent before " + renameOrDelete + " with sendNoop=false: " + recorded);
            assertEquals(0, recorded.stream().filter("NOOP"::equals).count(),
                    "No NOOP should be sent at all with sendNoop=false: " + recorded);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String delete = "ftp://admin@localhost:{{ftp.server.port}}/delete?password=admin&delete=true&delay=100";
                from(delete).routeId("deleteDefault").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:result");
                from(delete + "&sendNoop=false").routeId("deleteFalse").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:result");

                // the ftp consumer leaves the file where it is by default, so move it explicitly
                String move = "ftp://admin@localhost:{{ftp.server.port}}/move?password=admin&move=done&delay=100";
                from(move).routeId("moveDefault").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:result");
                from(move + "&sendNoop=false").routeId("moveFalse").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
