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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The file name used to build the path of a polled file comes from the directory listing returned by the remote server,
 * so it is not guaranteed to be a single path segment. Verifies the resolved path is kept inside the directory being
 * polled before it is used as the operand for retrieving, deleting or renaming. A listed file that the SFTP server
 * reports as a symbolic link is followed by the server when it is retrieved, so its target must be inside the directory
 * too.
 */
class RemoteFileConsumerStartingDirectoryJailTest extends CamelTestSupport {

    private RemoteFileConsumer<?> consumer(String uri) throws Exception {
        RemoteFileEndpoint<?> endpoint = context.getEndpoint(uri, RemoteFileEndpoint.class);
        return (RemoteFileConsumer<?>) endpoint.createConsumer(exchange -> {
        });
    }

    private AbstractSftpConsumer sftpConsumer(String uri, SftpOperations operations) {
        SftpEndpoint endpoint = context.getEndpoint(uri, SftpEndpoint.class);
        return new SftpConsumer(endpoint, exchange -> {
        }, operations, null);
    }

    @Test
    void shouldAcceptPathsWithinTheStartingDirectory() throws Exception {
        RemoteFileConsumer<?> consumer = consumer("ftp://hostname/poll");

        assertTrue(consumer.isWithinStartingDirectory("poll/file.txt"));
        assertTrue(consumer.isWithinStartingDirectory("poll/sub/file.txt"));
        // a ../ that still resolves back inside the polled directory is legitimate
        assertTrue(consumer.isWithinStartingDirectory("poll/sub/../file.txt"));
    }

    @Test
    void shouldRejectPathsEscapingTheStartingDirectory() throws Exception {
        RemoteFileConsumer<?> consumer = consumer("ftp://hostname/poll");

        assertFalse(consumer.isWithinStartingDirectory("poll/a/../../../secret.txt"));
        assertFalse(consumer.isWithinStartingDirectory("poll/../secret.txt"));
        assertFalse(consumer.isWithinStartingDirectory("poll/../../etc/shadow"));
        // a sibling directory whose name merely extends the polled directory name is not contained
        assertFalse(consumer.isWithinStartingDirectory("poll/../pollute/secret.txt"));
        assertFalse(consumer.isWithinStartingDirectory(null));
    }

    @Test
    void shouldRejectPathsEscapingTheStartingDirectoryOverSftp() throws Exception {
        RemoteFileConsumer<?> consumer = consumer("sftp://hostname/poll");

        assertTrue(consumer.isWithinStartingDirectory("poll/file.txt"));
        assertFalse(consumer.isWithinStartingDirectory("poll/a/../../../secret.txt"));
        assertFalse(consumer.isWithinStartingDirectory("poll/../secret.txt"));
    }

    @Test
    void shouldRejectUpwardsPathsWhenPollingTheSessionRoot() throws Exception {
        RemoteFileConsumer<?> consumer = consumer("ftp://hostname");

        assertTrue(consumer.isWithinStartingDirectory("file.txt"));
        // no directory is configured, but navigating above the session root still escapes
        assertFalse(consumer.isWithinStartingDirectory("../secret.txt"));
    }

    @Test
    void shouldNotResolveListedFilesThatAreNotSymbolicLinks() {
        StubSftpOperations server = new StubSftpOperations().file("/home/camel/poll/hello.txt");
        AbstractSftpConsumer consumer = sftpConsumer("sftp://hostname/poll", server);

        assertTrue(consumer.isWithinStartingDirectory(listed("poll", "hello.txt", false), "poll/hello.txt"));
        assertTrue(consumer.isWithinStartingDirectory(listed("poll", "sub/hello.txt", false), "poll/sub/hello.txt"));
        // the path built from the listed name is still checked
        assertFalse(consumer.isWithinStartingDirectory(listed("poll", "../hello.txt", false), "poll/../hello.txt"));
        // without asking the server
        assertEquals(0, server.lookups);
    }

    @Test
    void shouldAcceptSymbolicLinkResolvingInsideTheStartingDirectory() {
        StubSftpOperations server = new StubSftpOperations()
                .file("/home/camel/poll/hello.txt")
                .file("/home/camel/poll/data/report.txt")
                .link("/home/camel/poll/latest.txt", "hello.txt")
                .link("/home/camel/poll/report.txt", "/home/camel/poll/data/report.txt")
                .link("/home/camel/poll/current", "data")
                .link("/home/camel/poll/current.txt", "current/report.txt")
                .link("/home/camel/poll/again.txt", "../poll/latest.txt")
                .link("/home/camel/poll/data/hello.txt", "../hello.txt");
        AbstractSftpConsumer consumer = sftpConsumer("sftp://hostname/poll", server);

        // relative to the directory of the link, absolute, through a link to a directory, and to another link
        assertTrue(consumer.isWithinStartingDirectory(listed("poll", "latest.txt", true), "poll/latest.txt"));
        assertTrue(consumer.isWithinStartingDirectory(listed("poll", "report.txt", true), "poll/report.txt"));
        assertTrue(consumer.isWithinStartingDirectory(listed("poll", "current.txt", true), "poll/current.txt"));
        assertTrue(consumer.isWithinStartingDirectory(listed("poll", "again.txt", true), "poll/again.txt"));
        // listed in a sub directory (recursive)
        assertTrue(consumer.isWithinStartingDirectory(listed("poll", "data/hello.txt", true), "poll/data/hello.txt"));

        // a starting directory that is itself a link
        StubSftpOperations linkedServer = new StubSftpOperations()
                .file("/srv/inbox/hello.txt")
                .link("/home/camel/inbox", "/srv/inbox")
                .link("/srv/inbox/latest.txt", "hello.txt");
        AbstractSftpConsumer linkedConsumer = sftpConsumer("sftp://hostname/inbox", linkedServer);

        assertTrue(linkedConsumer.isWithinStartingDirectory(listed("inbox", "latest.txt", true), "inbox/latest.txt"));
    }

    @Test
    void shouldSkipSymbolicLinkResolvingOutsideTheStartingDirectory() {
        StubSftpOperations server = new StubSftpOperations()
                .file("/home/camel/poll/hello.txt")
                .file("/home/camel/archive/report.txt")
                .file("/home/camel/polling/report.txt")
                .link("/home/camel/poll/report.txt", "../archive/report.txt")
                .link("/home/camel/poll/absolute.txt", "/home/camel/archive/report.txt")
                .link("/home/camel/poll/archive", "../archive")
                .link("/home/camel/poll/through.txt", "archive/report.txt")
                .link("/home/camel/poll/polling.txt", "../polling/report.txt")
                .link("/home/camel/poll/sub/report.txt", "../../archive/report.txt");
        AbstractSftpConsumer consumer = sftpConsumer("sftp://hostname/poll", server);

        // relative upwards, absolute, and through a link to a directory outside
        assertFalse(consumer.isWithinStartingDirectory(listed("poll", "report.txt", true), "poll/report.txt"));
        assertFalse(consumer.isWithinStartingDirectory(listed("poll", "absolute.txt", true), "poll/absolute.txt"));
        assertFalse(consumer.isWithinStartingDirectory(listed("poll", "through.txt", true), "poll/through.txt"));
        // a sibling directory whose name merely extends the polled directory name is not contained
        assertFalse(consumer.isWithinStartingDirectory(listed("poll", "polling.txt", true), "poll/polling.txt"));
        // listed in a sub directory (recursive)
        assertFalse(consumer.isWithinStartingDirectory(listed("poll", "sub/report.txt", true), "poll/sub/report.txt"));
    }

    @Test
    void shouldSkipSymbolicLinkThatCannotBeResolved() {
        StubSftpOperations server = new StubSftpOperations()
                .file("/home/camel/poll/hello.txt")
                .link("/home/camel/poll/missing.txt", "gone.txt")
                .link("/home/camel/poll/ping.txt", "pong.txt")
                .link("/home/camel/poll/pong.txt", "ping.txt")
                .link("/home/camel/poll/empty.txt", "");
        AbstractSftpConsumer consumer = sftpConsumer("sftp://hostname/poll", server);

        // a dangling link, a loop, and a link without a target
        assertFalse(consumer.isWithinStartingDirectory(listed("poll", "missing.txt", true), "poll/missing.txt"));
        assertFalse(consumer.isWithinStartingDirectory(listed("poll", "ping.txt", true), "poll/ping.txt"));
        assertFalse(consumer.isWithinStartingDirectory(listed("poll", "empty.txt", true), "poll/empty.txt"));
    }

    private static Supplier<GenericFile<SftpRemoteFile>> listed(
            String directory, String relativeFilePath, boolean symbolicLink) {
        RemoteFile<SftpRemoteFile> file = new RemoteFile<>();
        file.setFile(new SftpRemoteFileSingle(FileUtil.stripPath(relativeFilePath)) {
            @Override
            public boolean isSymbolicLink() {
                return symbolicLink;
            }
        });
        file.setEndpointPath(directory);
        file.setRelativeFilePath(relativeFilePath);
        file.setAbsoluteFilePath(directory + "/" + relativeFilePath);
        return () -> file;
    }

    /**
     * The files of an SFTP server whose home directory is /home/camel: the paths that exist, and the symbolic links
     * among them.
     */
    private static final class StubSftpOperations extends SftpOperations {

        private final Set<String> files = new HashSet<>();
        private final Map<String, String> links = new HashMap<>();
        private int lookups;

        StubSftpOperations file(String path) {
            // the file and the directories it is in
            for (String name = path; name != null && !"/".equals(name); name = FileUtil.onlyPath(name)) {
                files.add(name);
            }
            return this;
        }

        StubSftpOperations link(String path, String target) {
            file(FileUtil.onlyPath(path));
            links.put(path, target);
            return this;
        }

        @Override
        public String getHomeDirectory() {
            return "/home/camel";
        }

        @Override
        public boolean isSymbolicLink(String path) {
            lookups++;
            if (links.containsKey(path)) {
                return true;
            }
            if (files.contains(path)) {
                return false;
            }
            throw new GenericFileOperationFailedException("No such file: " + path);
        }

        @Override
        public String readSymbolicLink(String path) {
            return links.get(path);
        }
    }
}
