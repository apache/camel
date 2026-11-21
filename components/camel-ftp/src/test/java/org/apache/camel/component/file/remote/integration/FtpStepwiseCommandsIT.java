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
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.component.file.remote.FtpEndpoint;
import org.apache.camel.component.file.remote.FtpOperations;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.infra.ftp.services.embedded.EmbeddedConfiguration;
import org.apache.camel.test.infra.ftp.services.embedded.FtpEmbeddedService;
import org.apache.camel.util.FileUtil;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With the default stepwise and autoCreate, an upload does not ask for the current directory, change into the directory
 * of its file and back, nor check that the directory exists, for each file; and the producer does not send a noop when
 * the previous upload completed a moment ago (CAMEL-26657).
 */
public class FtpStepwiseCommandsIT extends BaseServerTestSupport {

    // by command, such as CWD
    private final Map<String, AtomicInteger> commands = new ConcurrentHashMap<>();

    @RegisterExtension
    public FtpEmbeddedService service = new FtpEmbeddedService() {
        @Override
        protected FtpServerFactory createFtpServerFactory(EmbeddedConfiguration embeddedConfiguration) {
            FtpServerFactory factory = super.createFtpServerFactory(embeddedConfiguration);
            factory.setFtplets(Map.of("counter", new DefaultFtplet() {
                @Override
                public FtpletResult beforeCommand(FtpSession session, FtpRequest request)
                        throws FtpException, IOException {
                    commands.computeIfAbsent(request.getCommand().toUpperCase(), k -> new AtomicInteger()).incrementAndGet();
                    return super.beforeCommand(session, request);
                }
            }));
            return factory;
        }
    };

    private String ftpUri(String directory) {
        return "ftp://admin@localhost:{{ftp.server.port}}/" + directory + "?password=admin";
    }

    private int count(String command) {
        AtomicInteger count = commands.get(command);
        return count != null ? count.get() : 0;
    }

    @Test
    public void uploadsIntoTheSameDirectorySendOnlyTheFile() throws Exception {
        String uri = ftpUri("p1/out");
        // connects, creates the directory and changes into it
        template.sendBodyAndHeader(uri, "warm-up", Exchange.FILE_NAME, "warm-up.txt");

        commands.clear();
        for (int i = 0; i < 10; i++) {
            template.sendBodyAndHeader(uri, "message " + i, Exchange.FILE_NAME, "file-" + i + ".txt");
        }

        // a data connection and a STOR per file: no PWD, no CWD into the directory and back, no check that the
        // directory exists, and no NOOP as each upload follows the previous one right away
        assertEquals(10, count("STOR"), "commands: " + commands);
        assertEquals(0, count("PWD"), "commands: " + commands);
        assertEquals(0, count("CWD"), "commands: " + commands);
        assertEquals(0, count("CDUP"), "commands: " + commands);
        assertEquals(0, count("NOOP"), "commands: " + commands);
        assertEquals("message 7", Files.readString(service.ftpFile("p1/out/file-7.txt")));
    }

    @Test
    public void directoryRemovedBetweenUploadsIsCreatedAgain() throws Exception {
        String uri = ftpUri("p1/out");
        template.sendBodyAndHeader(uri, "first", Exchange.FILE_NAME, "first.txt");

        // removed by someone else: autoCreate remembers that it created it on this connection
        FileUtil.removeDir(service.ftpFile("p1").toFile());

        template.sendBodyAndHeader(uri, "second", Exchange.FILE_NAME, "second.txt");

        assertEquals("second", Files.readString(service.ftpFile("p1/out/second.txt")));
    }

    @Test
    public void clientIsInTheStartDirectoryAfterAStepwiseUpload() throws Exception {
        Files.createDirectories(service.ftpFile("p1/out"));
        @SuppressWarnings("unchecked")
        FtpEndpoint<FTPFile> endpoint = context.getEndpoint(ftpUri(""), FtpEndpoint.class);
        FtpOperations operations = (FtpOperations) endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);
        try {
            String start = operations.getCurrentDirectory();
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody("content");

            assertTrue(operations.storeFile("p1/out/a.txt", exchange, -1));

            // the upload left the connection in the directory of the file: the client that code outside the
            // operations uses with names relative to the start directory is changed back first
            assertEquals(start, operations.getClient().printWorkingDirectory());
            assertTrue(operations.existsFile("p1/out/a.txt"));
        } finally {
            operations.disconnect();
        }
    }
}
