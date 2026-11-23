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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.component.file.remote.FtpEndpoint;
import org.apache.camel.component.file.remote.FtpOperations;
import org.apache.camel.component.file.strategy.FileMoveExistingStrategy;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.ByteArrayInputStreamCache;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.infra.ftp.services.embedded.EmbeddedConfiguration;
import org.apache.camel.test.infra.ftp.services.embedded.FtpEmbeddedService;
import org.apache.camel.util.FileUtil;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With the default stepwise and autoCreate, an upload does not ask for the current directory, nor check that the
 * directory exists, for each file (CAMEL-26657). It still changes into the directory of its file each time: a server
 * such as vsftpd keeps the directory itself, not its path, so a connection that stayed in a directory that was renamed
 * since would store the file in the renamed directory.
 */
public class FtpStepwiseCommandsIT extends BaseServerTestSupport {

    // the server refuses to store a file of this name, in a directory that exists
    private static final String REFUSED = "refused.txt";

    // by command, such as CWD
    private final Map<String, AtomicInteger> commands = new ConcurrentHashMap<>();
    // the commands that change or use a directory, with their argument, such as "CWD out", in the order received
    private final List<String> sequence = new CopyOnWriteArrayList<>();

    @RegisterExtension
    public FtpEmbeddedService service = new FtpEmbeddedService() {
        @Override
        protected FtpServerFactory createFtpServerFactory(EmbeddedConfiguration embeddedConfiguration) {
            FtpServerFactory factory = super.createFtpServerFactory(embeddedConfiguration);
            factory.setFtplets(Map.of("counter", new DefaultFtplet() {
                @Override
                public FtpletResult beforeCommand(FtpSession session, FtpRequest request)
                        throws FtpException, IOException {
                    String command = request.getCommand().toUpperCase();
                    commands.computeIfAbsent(command, k -> new AtomicInteger()).incrementAndGet();
                    if (List.of("CWD", "PWD", "MKD", "STOR", "DELE", "RNFR", "RNTO").contains(command)) {
                        sequence.add(request.hasArgument() ? command + " " + request.getArgument() : command);
                    }
                    if ("STOR".equalsIgnoreCase(request.getCommand()) && REFUSED.equals(request.getArgument())) {
                        session.write(new DefaultFtpReply(550, "Refused"));
                        return FtpletResult.SKIP;
                    }
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
    public void uploadsIntoTheSameDirectoryChangeIntoItWithoutAskingOrChecking() throws Exception {
        String uri = ftpUri("p1/out");
        // connects, creates the directory and changes into it
        template.sendBodyAndHeader(uri, "warm-up", Exchange.FILE_NAME, "warm-up.txt");

        commands.clear();
        sequence.clear();
        for (int i = 0; i < 10; i++) {
            template.sendBodyAndHeader(uri, "message " + i, Exchange.FILE_NAME, "file-" + i + ".txt");
        }

        // per file: back to the starting directory, stepwise into the directory of the file, and a data connection and
        // a STOR. No PWD, and no check that the directory exists. The NOOP that the producer skips within a second of
        // the previous upload is not counted here, as it depends on time: RemoteFileProducerPreWriteCheckTest checks it
        for (int i = 0; i < 10; i++) {
            assertEquals(List.of("CWD /", "CWD p1", "CWD out", "STOR file-" + i + ".txt"),
                    sequence.subList(i * 4, i * 4 + 4), "sequence: " + sequence);
        }
        assertEquals(40, sequence.size(), "sequence: " + sequence);
        assertEquals(0, count("CDUP"), "commands: " + commands);
        for (int i = 0; i < 10; i++) {
            assertEquals("message " + i, Files.readString(service.ftpFile("p1/out/file-" + i + ".txt")));
        }
    }

    @Test
    public void directoryRemovedBetweenUploadsIsCreatedAgain() throws Exception {
        String uri = ftpUri("p1/out");
        template.sendBodyAndHeader(uri, "first", Exchange.FILE_NAME, "first.txt");

        // removed by someone else while the connection stays in it
        FileUtil.removeDir(service.ftpFile("p1").toFile());
        commands.clear();

        template.sendBodyAndHeader(uri, "second", Exchange.FILE_NAME, "second.txt");

        assertEquals("second", Files.readString(service.ftpFile("p1/out/second.txt")));
        // the change into the remembered directory failed, so the directory was created again and the upload tried
        // once more
        assertEquals(1, count("STOR"), "commands: " + commands);
        assertTrue(count("MKD") >= 1, "commands: " + commands);
    }

    @Test
    public void directoryRenamedBetweenUploadsIsNotUsed() throws Exception {
        String uri = ftpUri("p1/out");
        template.sendBodyAndHeader(uri, "first", Exchange.FILE_NAME, "first.txt");

        // the receiving side hands the directory off and starts a new one, while the connection stays in it
        Files.move(service.ftpFile("p1/out"), service.ftpFile("p1/out.1"));
        Files.createDirectories(service.ftpFile("p1/out"));
        sequence.clear();

        template.sendBodyAndHeader(uri, "second", Exchange.FILE_NAME, "second.txt");

        assertEquals("second", Files.readString(service.ftpFile("p1/out/second.txt")));
        assertFalse(Files.exists(service.ftpFile("p1/out.1/second.txt")));
        // the embedded server resolves STOR against the path of its current directory, so it would store the file in
        // the new directory without the change of directory: what keeps a server that holds the directory itself
        // (vsftpd) from using the renamed one is that the upload changes into the path again
        assertEquals(List.of("CWD /", "CWD p1", "CWD out", "STOR second.txt"), sequence);
    }

    @Test
    public void streamCacheBodyIsUploadedAgainIntoARemovedDirectory() throws Exception {
        String uri = ftpUri("p1/out");
        template.sendBodyAndHeader(uri, "first", Exchange.FILE_NAME, "first.txt");

        FileUtil.removeDir(service.ftpFile("p1").toFile());
        commands.clear();

        // a stream body reaches the producer as a stream cache with the default stream caching: it is read again
        // from the start
        template.sendBodyAndHeader(uri, new ByteArrayInputStreamCache(new ByteArrayInputStream("second".getBytes())),
                Exchange.FILE_NAME, "second.txt");

        assertEquals("second", Files.readString(service.ftpFile("p1/out/second.txt")));
        assertTrue(count("MKD") >= 1, "commands: " + commands);
    }

    @Test
    public void refusedUploadIntoAnExistingDirectoryIsNotTriedAgain() throws Exception {
        String uri = ftpUri("p1/out");
        template.sendBodyAndHeader(uri, "first", Exchange.FILE_NAME, "first.txt");
        commands.clear();

        // the directory exists, so the refusal has another cause: the file is not uploaded twice
        assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "refused", Exchange.FILE_NAME, REFUSED));
        assertEquals(1, count("STOR"), "commands: " + commands);
        assertEquals(0, count("MKD"), "commands: " + commands);

        template.sendBodyAndHeader(uri, "next", Exchange.FILE_NAME, "next.txt");
        assertEquals("next", Files.readString(service.ftpFile("p1/out/next.txt")));
    }

    @Test
    public void readerBodyIsNotUploadedAgainIntoARemovedDirectory() throws Exception {
        String uri = ftpUri("p1/out");
        template.sendBodyAndHeader(uri, "first", Exchange.FILE_NAME, "first.txt");

        FileUtil.removeDir(service.ftpFile("p1").toFile());
        commands.clear();

        // a failed upload may have read the reader (which is not an InputStream), so a second upload could store a part
        // of it
        StringReader stream = new StringReader("second");
        assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, stream, Exchange.FILE_NAME, "second.txt"));
        assertEquals(0, count("STOR"), "commands: " + commands);
        assertEquals(0, count("MKD"), "commands: " + commands);

        // the next upload checks the directory again
        template.sendBodyAndHeader(uri, "third", Exchange.FILE_NAME, "third.txt");
        assertEquals("third", Files.readString(service.ftpFile("p1/out/third.txt")));
    }

    @Test
    public void consumerMovesIntoARemovedDirectoryAgain() throws Exception {
        Files.createDirectories(service.ftpFile("inbox"));
        Files.writeString(service.ftpFile("inbox/1.txt"), "1");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("1");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(ftpUri("inbox") + "&move=.done&initialDelay=0&delay=100")
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        await().atMost(10, TimeUnit.SECONDS).until(() -> Files.exists(service.ftpFile("inbox/.done/1.txt")));

        // removed by someone else while the consumer stays connected: only an upload remembers a directory, so the
        // move of the next file checks the directory and creates it again
        FileUtil.removeDir(service.ftpFile("inbox/.done").toFile());
        mock.reset();
        mock.expectedBodiesReceived("2");
        Files.writeString(service.ftpFile("inbox/2.txt"), "2");

        MockEndpoint.assertIsSatisfied(context);
        await().atMost(10, TimeUnit.SECONDS).until(() -> Files.exists(service.ftpFile("inbox/.done/2.txt")));
        assertFalse(Files.exists(service.ftpFile("inbox/2.txt")));
    }

    @Test
    public void uploadsIntoAnotherDirectoryAndWithoutPathUseTheStartDirectory() throws Exception {
        String uri = ftpUri("base");
        template.sendBodyAndHeader(uri, "1", Exchange.FILE_NAME, "a/1.txt");
        template.sendBodyAndHeader(uri, "2", Exchange.FILE_NAME, "b/2.txt");
        template.sendBodyAndHeader(uri, "3", Exchange.FILE_NAME, "a/3.txt");
        template.sendBodyAndHeader(uri, "x", Exchange.FILE_NAME, "x.txt");

        // each name is relative to the directory of the endpoint, whichever directory the previous upload left the
        // connection in
        assertEquals("1", Files.readString(service.ftpFile("base/a/1.txt")));
        assertEquals("2", Files.readString(service.ftpFile("base/b/2.txt")));
        assertEquals("3", Files.readString(service.ftpFile("base/a/3.txt")));
        assertEquals("x", Files.readString(service.ftpFile("base/x.txt")));
        assertFalse(Files.exists(service.ftpFile("base/b/a")));
        assertFalse(Files.exists(service.ftpFile("base/a/x.txt")));
    }

    @Test
    public void uploadAfterAChangeOfDirectoryThatFailedHalfWayUsesTheStartDirectory() throws Exception {
        Files.createDirectories(service.ftpFile("p1"));
        Files.createDirectories(service.ftpFile("p2"));
        // the operations themselves, as the producer disconnects after a failed upload
        FtpOperations operations = connect(ftpUri("") + "&autoCreate=false");
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody("content");

            // the change into p1 succeeds, the change into missing fails
            assertThrows(GenericFileOperationFailedException.class,
                    () -> operations.storeFile("p1/missing/a.txt", exchange, -1));
            assertTrue(operations.storeFile("p2/b.txt", exchange, -1));
        } finally {
            operations.disconnect();
        }

        assertEquals("content", Files.readString(service.ftpFile("p2/b.txt")));
        assertFalse(Files.exists(service.ftpFile("p1/p2")));
    }

    @Test
    public void operationsAfterAStepwiseUploadUseTheStartDirectory() throws Exception {
        Files.createDirectories(service.ftpFile("p1/out"));
        FtpOperations operations = connect(ftpUri(""));
        try {
            String start = operations.getCurrentDirectory();
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody("content");

            assertTrue(operations.storeFile("p1/out/a.txt", exchange, -1));

            // the upload left the connection in the directory of the file: the existence check, and the client that
            // code outside the operations uses with names relative to the start directory, change back first
            assertTrue(operations.existsFile("p1/out/a.txt"));
            assertTrue(operations.storeFile("p1/out/b.txt", exchange, -1));
            assertEquals(start, operations.getClient().printWorkingDirectory());
        } finally {
            operations.disconnect();
        }
    }

    @Test
    public void autoCreateRemembersOnlyTheMostRecentDirectories() throws Exception {
        String uri = ftpUri("base");
        // more directories than the operations remember, each checked or created once
        for (int i = 0; i <= 100; i++) {
            template.sendBodyAndHeader(uri, "x", Exchange.FILE_NAME, "d" + i + "/x.txt");
        }
        sequence.clear();

        // the first one was forgotten, so it is checked again; the last one is still known
        template.sendBodyAndHeader(uri, "y", Exchange.FILE_NAME, "d0/y.txt");
        template.sendBodyAndHeader(uri, "y", Exchange.FILE_NAME, "d100/y.txt");

        assertEquals(1, sequence.stream().filter("CWD base/d0"::equals).count(), "sequence: " + sequence);
        assertEquals(0, sequence.stream().filter("CWD base/d100"::equals).count(), "sequence: " + sequence);
        assertEquals("y", Files.readString(service.ftpFile("base/d0/y.txt")));
        assertEquals("y", Files.readString(service.ftpFile("base/d100/y.txt")));
    }

    @Test
    public void deleteFromAMoveExistingStrategyKeepsTheUploadInTheDirectoryOfTheFile() throws Exception {
        Files.createDirectories(service.ftpFile("p1/out"));
        Files.writeString(service.ftpFile("p1/out/a.txt"), "old");
        Files.writeString(service.ftpFile("p1/out/a.txt.bak"), "older");
        // as SftpDefaultMoveExistingFileStrategy does: delete the target through the operations, then rename
        context.getRegistry().bind("deleteThenRename", new FileMoveExistingStrategy() {
            @Override
            public boolean moveExistingFile(
                    GenericFileEndpoint<?> endpoint, GenericFileOperations<?> operations,
                    String fileName) {
                operations.deleteFile(fileName + ".bak");
                return operations.renameFile(fileName, fileName + ".bak");
            }
        });

        template.sendBodyAndHeader(ftpUri("p1/out") + "&fileExist=Move&moveExisting=${file:name}.bak"
                                   + "&moveExistingFileStrategy=#deleteThenRename",
                "new", Exchange.FILE_NAME, "a.txt");

        // the delete and the rename use names relative to the directory of the file, and the upload stays there
        assertEquals("new", Files.readString(service.ftpFile("p1/out/a.txt")));
        assertEquals("old", Files.readString(service.ftpFile("p1/out/a.txt.bak")));
        assertFalse(Files.exists(service.ftpFile("a.txt")));
    }

    private FtpOperations connect(String uri) throws Exception {
        @SuppressWarnings("unchecked")
        FtpEndpoint<FTPFile> endpoint = context.getEndpoint(uri, FtpEndpoint.class);
        FtpOperations operations = (FtpOperations) endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);
        return operations;
    }
}
