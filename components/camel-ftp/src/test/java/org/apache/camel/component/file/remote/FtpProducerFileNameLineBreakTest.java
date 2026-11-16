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
package org.apache.camel.component.file.remote;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The FTP control connection sends one command per line, so the ftp producer and the ftp operations refuse a file or
 * directory name with a carriage return or a line feed, and send nothing to the server for it.
 */
public class FtpProducerFileNameLineBreakTest extends CamelTestSupport {

    private static final String EXISTING = "/outbox/existing.txt";

    private FakeFtpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new FakeFtpServer();
        server.addFile(EXISTING, "existing".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
    }

    private String ftpUri(String options) {
        String uri = "ftp://camel@127.0.0.1:" + server.port()
                     + "/outbox?password=secret&passiveMode=true&soTimeout=5000&timeout=5000";
        return options.isEmpty() ? uri : uri + "&" + options;
    }

    private void addRoute(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:upload").to(ftpUri(options));
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "before\nafter.txt", "before\rafter.txt", "before\r\nafter.txt", "before\r\nafter/hello.txt",
            "sub/before\nafter.txt" })
    void fileNameWithLineBreakIsRefused(String name) throws Exception {
        addRoute("");

        assertRefused(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "stepwise=false", "tempFileName=${file:name}.tmp", "tempPrefix=tmp-", "fileExist=Move&moveExisting=old",
            "fileExist=Append", "fileExist=Fail", "fileExist=Ignore&fastExistsCheck=true", "chmod=640",
            "doneFileName=${file:name}.done" })
    void fileNameWithLineBreakIsRefusedWithOption(String options) throws Exception {
        addRoute(options);

        assertRefused("before\r\nafter.txt");
    }

    @Test
    void plainFileNameIsStored() throws Exception {
        addRoute("tempFileName=${file:name}.tmp&doneFileName=${file:name}.done&chmod=640");

        template.sendBodyAndHeader("direct:upload", "hello", Exchange.FILE_NAME, "hello.txt");

        assertEquals("hello", new String(server.files().get("/outbox/hello.txt"), StandardCharsets.UTF_8));
        assertTrue(server.files().containsKey("/outbox/hello.txt.done"));
    }

    @Test
    void operationsRefuseNameWithLineBreak() throws Exception {
        FtpEndpoint<FTPFile> endpoint = context.getEndpoint(ftpUri(""), FtpEndpoint.class);
        RemoteFileOperations<FTPFile> operations = endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);
        Exchange exchange = endpoint.createExchange();
        String name = "before\nafter.txt";
        try {
            List<Executable> calls = List.of(
                    () -> operations.deleteFile(name),
                    () -> operations.renameFile("existing.txt", name),
                    () -> operations.renameFile(name, "renamed.txt"),
                    () -> operations.buildDirectory(name, false),
                    () -> operations.existsFile(name),
                    () -> operations.changeCurrentDirectory(name),
                    () -> operations.listFiles(name),
                    () -> operations.storeFileDirectly(name, "hello"),
                    () -> operations.retrieveFile(name, exchange, 5));
            for (Executable call : calls) {
                GenericFileOperationFailedException e = assertThrows(GenericFileOperationFailedException.class, call);
                assertTrue(e.getMessage().contains("U+000A at index 6"), e.getMessage());
            }
        } finally {
            operations.disconnect();
        }

        assertNothingSentFor();
    }

    private void assertRefused(String name) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:upload", "hello", Exchange.FILE_NAME, name));
        GenericFileOperationFailedException cause = assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        assertTrue(cause.getMessage().contains("a carriage return or a line feed cannot be sent in an FTP command"),
                cause.getMessage());

        assertNothingSentFor();
        assertEquals(Set.of(EXISTING), server.files().keySet());
    }

    private void assertNothingSentFor() {
        List<String> sent = server.commands().stream()
                .filter(command -> command.contains("before") || command.contains("after"))
                .toList();
        assertEquals(List.of(), sent, "Commands received for the refused name: " + server.commands());
    }
}
