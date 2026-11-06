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

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.mina.MinaSftpEndpoint;
import org.apache.camel.component.file.remote.mina.MinaSftpOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ftp.services.embedded.SftpEmbeddedService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.function.ThrowingRunnable;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.apache.sshd.sftp.server.DirectoryHandle;
import org.apache.sshd.sftp.server.SftpEventListener;
import org.apache.sshd.sftp.server.SftpFileSystemAccessor;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemProxy;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mina-sftp component checks whether a file exists, and reads its size and modification time for readLock=changed,
 * with a single LSTAT request, whatever the number of files in the directory (CAMEL-26671). It lists the directory only
 * when the server does not answer that request for the path. readLock=changed consumes the file also with
 * fastExistsCheck=true (CAMEL-26648).
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class MinaSftpExistsFileIT extends CamelTestSupport {

    // a reply of the server to a directory listing request holds about 150 of these entries
    private static final int FILES = 1000;

    @RegisterExtension
    CountingSftpEmbeddedService service = new CountingSftpEmbeddedService();

    private Path base;

    @BeforeEach
    void createTestDirectory() throws IOException {
        // the root directory of the server is shared by the tests of this class and kept between runs
        base = Files.createTempDirectory(Files.createDirectories(service.getFtpRootDir()), "test");
    }

    @Test
    void existsFileIsOneRequestWhateverTheDirectorySize() throws Exception {
        createFiles(base.resolve("small"), 10);
        createFiles(base.resolve("big"), FILES);
        Files.createDirectories(base.resolve("big").resolve("sub"));

        MinaSftpOperations operations = connect();
        try {
            for (String directory : new String[] { "small", "big" }) {
                assertExists(operations, true, directory + "/file-0005.txt");
                assertExists(operations, false, directory + "/missing.txt");
            }
            assertExists(operations, true, "big/sub");
            assertExists(operations, false, "nodir/missing.txt");
        } finally {
            operations.disconnect();
        }
    }

    @Test
    void danglingSymbolicLinkExists() throws Exception {
        Path inbox = Files.createDirectories(base.resolve("inbox"));
        createSymbolicLink(inbox.resolve("link.txt"), Path.of("missing.txt"));

        MinaSftpOperations operations = connect();
        try {
            // the path itself, as in the directory listing: the link exists, though its target does not
            assertExists(operations, true, "inbox/link.txt");
            assertExists(operations, false, "inbox/missing.txt");
        } finally {
            operations.disconnect();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "PERMISSION_DENIED", "OP_UNSUPPORTED", "FAILURE", "BAD_MESSAGE", "NO_SUCH_PATH" })
    void listsDirectoryWhenServerDoesNotAnswerLstat(String status) throws Exception {
        createFiles(base.resolve("inbox"), 10);
        service.onRequest(SftpConstants.SSH_FXP_LSTAT, () -> {
            switch (status) {
                case "PERMISSION_DENIED" -> throw new AccessDeniedException("lstat");
                case "OP_UNSUPPORTED" -> throw new UnsupportedOperationException("lstat");
                // a status that does not say whether the path exists, and one of a later version of the protocol,
                // which some servers send
                case "BAD_MESSAGE" -> throw new SftpException(SftpConstants.SSH_FX_BAD_MESSAGE, "lstat");
                case "NO_SUCH_PATH" -> throw new SftpException(SftpConstants.SSH_FX_NO_SUCH_PATH, "lstat");
                default -> throw new IOException("lstat");
            }
        });

        MinaSftpOperations operations = connect();
        try {
            service.reset();
            assertTrue(operations.existsFile(remotePath("inbox/file-0005.txt")));
            assertFalse(operations.existsFile(remotePath("inbox/missing.txt")));
            assertFalse(operations.existsFile(remotePath("nodir/missing.txt")));
            assertEquals(3, service.requests(SftpConstants.SSH_FXP_LSTAT));
            // each was looked up in a listing of its directory, as the directory that does not exist
            assertEquals(3, service.requests(SftpConstants.SSH_FXP_OPENDIR), "requests: " + service.requests);
        } finally {
            operations.disconnect();
        }
    }

    @Test
    void listsDirectoryWhenServerAnswersNeitherLstatNorStatOfFile() throws Exception {
        createFiles(base.resolve("inbox"), 10);
        // a server that does not permit LSTAT on a path does not permit STAT either, while it lists the directory
        service.refuseAttributesOf(path -> !Files.isDirectory(path));

        MinaSftpOperations operations = connect();
        try {
            service.reset();
            assertTrue(operations.existsFile(remotePath("inbox/file-0005.txt")));
            assertFalse(operations.existsFile(remotePath("inbox/missing.txt")));
            assertEquals(2, service.requests(SftpConstants.SSH_FXP_LSTAT));
            assertEquals(2, service.requests(SftpConstants.SSH_FXP_OPENDIR), "requests: " + service.requests);
        } finally {
            operations.disconnect();
        }
    }

    @Test
    void failsWhenServerAnswersNeitherLstatNorListing() throws Exception {
        createFiles(base.resolve("inbox"), 10);
        service.onRequest(SftpConstants.SSH_FXP_LSTAT, () -> {
            throw new AccessDeniedException("lstat");
        });
        service.onRequest(SftpConstants.SSH_FXP_OPENDIR, () -> {
            throw new AccessDeniedException("opendir");
        });

        MinaSftpOperations operations = connect();
        try {
            // the check fails, it does not answer that the file is missing
            GenericFileOperationFailedException e = assertThrows(GenericFileOperationFailedException.class,
                    () -> operations.existsFile(remotePath("inbox/file-0005.txt")));
            SftpException cause = assertInstanceOf(SftpException.class, e.getCause());
            assertEquals(SftpConstants.SSH_FX_PERMISSION_DENIED, cause.getStatus());
        } finally {
            operations.disconnect();
        }
    }

    @Test
    void failsWhenConnectionIsLostDuringLstat() throws Exception {
        createFiles(base.resolve("inbox"), 10);

        MinaSftpOperations operations = connect();
        try {
            service.reset();
            // the server drops the connection when it receives the LSTAT request, before it answers
            service.onRequest(SftpConstants.SSH_FXP_LSTAT, service::disconnectAllSessions);
            GenericFileOperationFailedException e = assertThrows(GenericFileOperationFailedException.class,
                    () -> operations.existsFile(remotePath("inbox/file-0005.txt")));
            assertEquals(1, service.requests(SftpConstants.SSH_FXP_LSTAT));
            // the check fails, it does not answer that the file is missing, nor lists the directory
            assertInstanceOf(IOException.class, e.getCause());
            assertFalse(e.getCause() instanceof SftpException, "cause: " + e.getCause());
            assertEquals(0, service.requests(SftpConstants.SSH_FXP_OPENDIR), "requests: " + service.requests);
        } finally {
            operations.disconnect();
        }
    }

    @Test
    void fileExistFailDoesNotListDirectory() throws Exception {
        Path out = base.resolve("out");
        createFiles(out, FILES);
        String uri = uri("out") + "&autoCreate=false&fileExist=Fail";
        // connect before counting
        template.sendBodyAndHeader(uri, "first", Exchange.FILE_NAME, "first.txt");
        service.reset();

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "replaced", Exchange.FILE_NAME, "file-0005.txt"));
        assertInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        template.sendBodyAndHeader(uri, "written", Exchange.FILE_NAME, "new.txt");

        assertEquals("x", Files.readString(out.resolve("file-0005.txt")));
        assertEquals("written", Files.readString(out.resolve("new.txt")));
        assertEquals(2, service.requests(SftpConstants.SSH_FXP_LSTAT));
        assertEquals(0, service.requests(SftpConstants.SSH_FXP_OPENDIR), "requests: " + service.requests);
        assertEquals(0, service.entriesListed());
    }

    @Test
    void fileExistFailFindsDanglingSymbolicLink() throws Exception {
        Path out = Files.createDirectories(base.resolve("out"));
        createSymbolicLink(out.resolve("link.txt"), Path.of("missing.txt"));
        String uri = uri("out") + "&autoCreate=false&fileExist=Fail";

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "written", Exchange.FILE_NAME, "link.txt"));
        GenericFileOperationFailedException cause = assertInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith("File already exist"), cause.getMessage());
        // nothing was written through the link
        assertFalse(Files.exists(out.resolve("missing.txt")));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void readLockChangedDoesNotListDirectory(boolean fastExistsCheck) throws Exception {
        createFiles(base.resolve("inbox"), FILES);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        consume("inbox", "&noop=true&maxMessagesPerPoll=1&readLock=changed&readLockCheckInterval=10&fastExistsCheck="
                         + fastExistsCheck);

        mock.assertIsSatisfied();
        // at least two checks, with the same size and modification time
        assertTrue(service.requests(SftpConstants.SSH_FXP_LSTAT) >= 2, "requests: " + service.requests);
        // only the poll lists the directory, and stops at the first file
        assertEquals(1, service.requests(SftpConstants.SSH_FXP_OPENDIR), "requests: " + service.requests);
        assertTrue(service.entriesListed() < FILES, "entries listed: " + service.entriesListed());
    }

    @Test
    void readLockChangedWithFastExistsCheckConsumesAndDeletesFile() throws Exception {
        Path inbox = Files.createDirectories(base.resolve("inbox"));
        Files.writeString(inbox.resolve("hello.txt"), "Hello World");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        consume("inbox", "&delete=true&readLock=changed&readLockCheckInterval=10&readLockTimeout=5000"
                         + "&fastExistsCheck=true");

        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !Files.exists(inbox.resolve("hello.txt")));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void readLockChangedConsumesSymbolicLink(boolean fastExistsCheck) throws Exception {
        Path inbox = Files.createDirectories(base.resolve("inbox"));
        Files.writeString(Files.createDirectories(inbox.resolve("data")).resolve("data.txt"), "Linked");
        createSymbolicLink(inbox.resolve("link.txt"), Path.of("data", "data.txt"));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Linked");
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "link.txt");

        // the size and modification time of the link itself, as the directory listing reports them
        consume("inbox", "&noop=true&readLock=changed&readLockCheckInterval=10&readLockTimeout=5000&fastExistsCheck="
                         + fastExistsCheck);

        mock.assertIsSatisfied();
    }

    @Test
    void readLockChangedListsDirectoryWhenServerDoesNotAnswerLstat() throws Exception {
        createFiles(base.resolve("inbox"), 3);
        service.onRequest(SftpConstants.SSH_FXP_LSTAT, () -> {
            throw new AccessDeniedException("lstat");
        });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        consume("inbox", "&noop=true&maxMessagesPerPoll=1&readLock=changed&readLockCheckInterval=10&readLockTimeout=5000");

        mock.assertIsSatisfied();
        // the poll, and at least two checks
        assertTrue(service.requests(SftpConstants.SSH_FXP_OPENDIR) >= 3, "requests: " + service.requests);
    }

    @Test
    void readLockChangedWaitsUntilFileStopsChanging() throws Exception {
        Path inbox = Files.createDirectories(base.resolve("inbox"));
        Path file = inbox.resolve("growing.txt");
        Files.writeString(file, "line 0\n");
        // the file grows before each of the first three checks reads its attributes
        AtomicInteger checks = new AtomicInteger();
        service.onRequest(SftpConstants.SSH_FXP_LSTAT, () -> {
            int check = checks.incrementAndGet();
            if (check <= 3) {
                Files.writeString(file, "line " + check + "\n", StandardOpenOption.APPEND);
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("line 0\nline 1\nline 2\nline 3\n");

        consume("inbox", "&noop=true&readLock=changed&readLockCheckInterval=10");

        mock.assertIsSatisfied();
        // the last two checks found the same size and modification time
        assertTrue(checks.get() >= 4, "checks: " + checks.get());
    }

    private MinaSftpOperations connect() {
        MinaSftpEndpoint endpoint = context.getEndpoint(uri(""), MinaSftpEndpoint.class);
        MinaSftpOperations operations = (MinaSftpOperations) endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);
        return operations;
    }

    private void assertExists(MinaSftpOperations operations, boolean expected, String name) {
        service.reset();
        assertEquals(expected, operations.existsFile(remotePath(name)), name);
        // a single LSTAT request, and no directory listing
        assertEquals(1, service.requests(SftpConstants.SSH_FXP_LSTAT), name + ", requests: " + service.requests);
        assertEquals(1, service.requests(), name + ", requests: " + service.requests);
        assertEquals(0, service.entriesListed(), name);
    }

    private String remotePath(String name) {
        // relative to the home directory of the server, as the test directory
        return base + "/" + name;
    }

    private String uri(String directory) {
        return "mina-sftp://localhost:" + service.getPort() + "/" + base + "/" + directory
               + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile();
    }

    private void consume(String directory, String query) throws Exception {
        service.reset();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri(directory) + "&autoCreate=false&initialDelay=0&delay=60000" + query)
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });
    }

    private static void createFiles(Path directory, int count) throws IOException {
        Files.createDirectories(directory);
        for (int i = 0; i < count; i++) {
            Files.writeString(directory.resolve(String.format("file-%04d.txt", i)), "x");
        }
    }

    private static void createSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalStateException(e);
        } catch (UnsupportedOperationException | FileSystemException e) {
            // such as Windows when the user may not create symbolic links
            Assumptions.abort("Symbolic links cannot be created on this platform: " + e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The embedded SFTP server, counting the requests it receives and the directory entries it sends, and running a
     * hook before it answers a request of some type: a hook that throws makes the server answer with an error status.
     * It can also refuse the LSTAT and STAT requests on some paths only.
     */
    static class CountingSftpEmbeddedService extends SftpEmbeddedService {

        // by the name of the request type, such as SSH_FXP_LSTAT
        private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();
        private final AtomicInteger entriesListed = new AtomicInteger();
        private final Map<Integer, ThrowingRunnable<IOException>> hooks = new ConcurrentHashMap<>();
        private volatile Predicate<Path> refuseAttributesOf;

        @Override
        public void setUpServer() throws Exception {
            super.setUpServer();
            SftpSubsystemFactory factory = (SftpSubsystemFactory) sshd.getSubsystemFactories().get(0);
            factory.setFileSystemAccessor(new SftpFileSystemAccessor() {
                @Override
                public LinkOption[] resolveFileAccessLinkOptions(
                        SftpSubsystemProxy subsystem, Path file, int cmd, String extension, boolean followLinks)
                        throws IOException {
                    Predicate<Path> refused = refuseAttributesOf;
                    if ((cmd == SftpConstants.SSH_FXP_LSTAT || cmd == SftpConstants.SSH_FXP_STAT) && refused != null
                            && refused.test(file)) {
                        throw new AccessDeniedException(file.toString());
                    }
                    return SftpFileSystemAccessor.super.resolveFileAccessLinkOptions(
                            subsystem, file, cmd, extension, followLinks);
                }
            });
            factory.addSftpEventListener(new SftpEventListener() {
                @Override
                public void received(ServerSession session, int type, int id) throws IOException {
                    requests.computeIfAbsent(SftpConstants.getCommandMessageName(type), k -> new AtomicInteger())
                            .incrementAndGet();
                    ThrowingRunnable<IOException> hook = hooks.get(type);
                    if (hook != null) {
                        hook.run();
                    }
                }

                @Override
                public void readEntries(
                        ServerSession session, String remoteHandle, DirectoryHandle localHandle, Map<String, Path> entries) {
                    entriesListed.addAndGet(entries.size());
                }
            });
        }

        /**
         * Runs the hook when the server receives a request of the type, before it answers it.
         */
        void onRequest(int type, ThrowingRunnable<IOException> hook) {
            hooks.put(type, hook);
        }

        /**
         * Answers the LSTAT and STAT requests on the paths that match with PERMISSION_DENIED.
         */
        void refuseAttributesOf(Predicate<Path> paths) {
            this.refuseAttributesOf = paths;
        }

        void reset() {
            requests.clear();
            entriesListed.set(0);
        }

        int requests(int type) {
            AtomicInteger count = requests.get(SftpConstants.getCommandMessageName(type));
            return count != null ? count.get() : 0;
        }

        int requests() {
            return requests.values().stream().mapToInt(AtomicInteger::get).sum();
        }

        int entriesListed() {
            return entriesListed.get();
        }
    }
}
