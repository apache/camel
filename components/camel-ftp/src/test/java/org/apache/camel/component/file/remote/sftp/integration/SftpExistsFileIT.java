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

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.SftpEndpoint;
import org.apache.camel.component.file.remote.SftpOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ftp.services.embedded.SftpEmbeddedService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.function.ThrowingRunnable;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.server.DirectoryHandle;
import org.apache.sshd.sftp.server.SftpEventListener;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sftp component checks whether a file exists, and reads its size and modification time for readLock=changed, with
 * a single LSTAT request, whatever the number of files in the directory (CAMEL-26656). It lists the directory only when
 * the server does not answer that request for the path.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class SftpExistsFileIT extends CamelTestSupport {

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

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void existsFileIsOneRequestWhateverTheDirectorySize(boolean fastExistsCheck) throws Exception {
        createFiles(base.resolve("small"), 10);
        createFiles(base.resolve("big"), FILES);
        Files.createDirectories(base.resolve("big").resolve("sub"));

        SftpOperations operations = connect(fastExistsCheck);
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

    @ParameterizedTest
    @CsvSource({
            "PERMISSION_DENIED, false", "OP_UNSUPPORTED, false", "FAILURE, false",
            "PERMISSION_DENIED, true", "OP_UNSUPPORTED, true", "FAILURE, true" })
    void listsDirectoryWhenServerDoesNotAnswerLstat(String status, boolean fastExistsCheck) throws Exception {
        createFiles(base.resolve("inbox"), 10);
        service.onLstat(() -> {
            switch (status) {
                case "PERMISSION_DENIED" -> throw new AccessDeniedException("lstat");
                case "OP_UNSUPPORTED" -> throw new UnsupportedOperationException("lstat");
                default -> throw new IOException("lstat");
            }
        });

        SftpOperations operations = connect(fastExistsCheck);
        try {
            service.reset();
            assertTrue(operations.existsFile(remotePath("inbox/file-0005.txt")));
            assertFalse(operations.existsFile(remotePath("inbox/missing.txt")));
            assertEquals(2, service.requests(SftpConstants.SSH_FXP_LSTAT));
            // the existing file was found in a listing of its directory, as before
            assertTrue(service.requests(SftpConstants.SSH_FXP_OPENDIR) >= 1, "requests: " + service.requests);
        } finally {
            operations.disconnect();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void danglingSymbolicLinkExists(boolean fastExistsCheck) throws Exception {
        Path inbox = Files.createDirectories(base.resolve("inbox"));
        createSymbolicLink(inbox.resolve("link.txt"), Path.of("missing.txt"));

        SftpOperations operations = connect(fastExistsCheck);
        try {
            // the path itself, as in the directory listing: the link exists, though its target does not
            assertExists(operations, true, "inbox/link.txt");
            assertExists(operations, false, "inbox/missing.txt");
        } finally {
            operations.disconnect();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void fileExistFailDoesNotListDirectory(boolean fastExistsCheck) throws Exception {
        Path out = base.resolve("out");
        createFiles(out, FILES);
        String uri = sftpUri("out") + "&autoCreate=false&fileExist=Fail&fastExistsCheck=" + fastExistsCheck;
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
    void tempFileNameReplacesExistingFileWithoutListingDirectory() throws Exception {
        Path out = base.resolve("out");
        createFiles(out, FILES);
        String uri = sftpUri("out") + "&autoCreate=false&tempFileName=${file:onlyname}.inprogress";
        // connect before counting
        template.sendBodyAndHeader(uri, "first", Exchange.FILE_NAME, "first.txt");
        service.reset();

        template.sendBodyAndHeader(uri, "replaced", Exchange.FILE_NAME, "file-0005.txt");

        assertEquals("replaced", Files.readString(out.resolve("file-0005.txt")));
        assertFalse(Files.exists(out.resolve("file-0005.txt.inprogress")));
        // the target and the temp file
        assertEquals(2, service.requests(SftpConstants.SSH_FXP_LSTAT));
        assertEquals(0, service.requests(SftpConstants.SSH_FXP_OPENDIR), "requests: " + service.requests);
        assertEquals(0, service.entriesListed());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void readLockChangedDoesNotListDirectory(boolean fastExistsCheck) throws Exception {
        createFiles(base.resolve("inbox"), FILES);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        consume("inbox", "&maxMessagesPerPoll=1&readLock=changed&readLockCheckInterval=10&fastExistsCheck="
                         + fastExistsCheck);

        mock.assertIsSatisfied();
        // at least two checks, with the same size and modification time
        assertTrue(service.requests(SftpConstants.SSH_FXP_LSTAT) >= 2, "requests: " + service.requests);
        // only the poll lists the directory, and stops at the first file
        assertEquals(1, service.requests(SftpConstants.SSH_FXP_OPENDIR), "requests: " + service.requests);
        assertTrue(service.entriesListed() < FILES, "entries listed: " + service.entriesListed());
    }

    @Test
    void readLockChangedWaitsUntilFileStopsChanging() throws Exception {
        Path inbox = Files.createDirectories(base.resolve("inbox"));
        Path file = inbox.resolve("growing.txt");
        Files.writeString(file, "line 0\n");
        // the file grows before each of the first three checks reads its attributes
        AtomicInteger checks = new AtomicInteger();
        service.onLstat(() -> {
            int check = checks.incrementAndGet();
            if (check <= 3) {
                Files.writeString(file, "line " + check + "\n", StandardOpenOption.APPEND);
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("line 0\nline 1\nline 2\nline 3\n");

        consume("inbox", "&readLock=changed&readLockCheckInterval=10");

        mock.assertIsSatisfied();
        // the last two checks found the same size and modification time
        assertTrue(checks.get() >= 4, "checks: " + checks.get());
    }

    private SftpOperations connect(boolean fastExistsCheck) {
        SftpEndpoint endpoint = context.getEndpoint(sftpUri("") + "&fastExistsCheck=" + fastExistsCheck, SftpEndpoint.class);
        SftpOperations operations = (SftpOperations) endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);
        // JSch asks the server for the home directory once, to resolve the relative paths against it
        operations.getCurrentDirectory();
        return operations;
    }

    private void assertExists(SftpOperations operations, boolean expected, String name) {
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

    private String sftpUri(String directory) {
        return "sftp://localhost:" + service.getPort() + "/" + base + "/" + directory
               + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile();
    }

    private void consume(String directory, String query) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // autoCreate checks that the directory exists by reading the first reply of a listing
                from(sftpUri(directory) + "&autoCreate=false&initialDelay=0&delay=60000&noop=true" + query)
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
     * hook before it answers an LSTAT request: a hook that throws makes the server answer with an error status.
     */
    static class CountingSftpEmbeddedService extends SftpEmbeddedService {

        // by the name of the request type, such as SSH_FXP_LSTAT
        private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();
        private final AtomicInteger entriesListed = new AtomicInteger();
        private volatile ThrowingRunnable<IOException> onLstat;

        @Override
        public void setUpServer() throws Exception {
            super.setUpServer();
            SftpSubsystemFactory factory = (SftpSubsystemFactory) sshd.getSubsystemFactories().get(0);
            factory.addSftpEventListener(new SftpEventListener() {
                @Override
                public void received(ServerSession session, int type, int id) throws IOException {
                    requests.computeIfAbsent(SftpConstants.getCommandMessageName(type), k -> new AtomicInteger())
                            .incrementAndGet();
                    ThrowingRunnable<IOException> hook = onLstat;
                    if (type == SftpConstants.SSH_FXP_LSTAT && hook != null) {
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

        void onLstat(ThrowingRunnable<IOException> hook) {
            this.onLstat = hook;
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
