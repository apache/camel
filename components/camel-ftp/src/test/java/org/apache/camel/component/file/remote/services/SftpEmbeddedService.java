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

package org.apache.camel.component.file.remote.services;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import org.apache.camel.test.infra.common.services.AbstractTestService;
import org.apache.camel.test.infra.ftp.common.FtpProperties;
import org.apache.camel.test.infra.ftp.services.FtpService;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.scp.ScpCommandFactory;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SftpEmbeddedService extends AbstractTestService implements FtpService {
    private static final Logger LOG = LoggerFactory.getLogger(SftpEmbeddedService.class);
    private static final String KNOWN_HOSTS = "[localhost]:%d ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQDdfIWeSV4o68dRrKS"
                                              + "zFd/Bk51E65UTmmSrmW0O1ohtzi6HzsDPjXgCtlTt3FqTcfFfI92IlTr4JWqC9UK1QT1ZTeng0MkPQmv68hDANHbt5CpETZHjW5q4OOgWhV"
                                              + "vj5IyOC2NZHtKlJBkdsMAa15ouOOJLzBvAvbqOR/yUROsEiQ==";

    protected SshServer sshd;
    protected final boolean rootDirMode;
    protected int port;

    private Path rootDir;
    private Path knownHosts;

    public SftpEmbeddedService() {
        this(false);
    }

    public SftpEmbeddedService(boolean rootDirMode) {
        this.rootDirMode = rootDirMode;
    }

    public void setUp() throws Exception {
        rootDir = testDirectory().resolve("res/home");
        knownHosts = testDirectory().resolve("user-home/.ssh/known_hosts");
        Files.createDirectories(knownHosts.getParent());
        Files.write(knownHosts, buildKnownHosts());
        setUpServer();
    }

    private Path testDirectory() {
        return Paths.get("target", "ftp", context.getRequiredTestClass().getSimpleName());
    }

    public void setUpServer() throws Exception {
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(port);
        sshd.setKeyPairProvider(new FileKeyPairProvider(Paths.get("src/test/resources/hostkey.pem")));
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshd.setCommandFactory(new ScpCommandFactory());
        sshd.setPasswordAuthenticator((username, password, session) -> true);
        sshd.setPublickeyAuthenticator(getPublickeyAuthenticator());

        if (rootDirMode) {
            sshd.setFileSystemFactory(new VirtualFileSystemFactory(testDirectory().resolve("res").toAbsolutePath()));
        }

        sshd.start();

        port = ((InetSocketAddress) sshd.getBoundAddresses().iterator().next()).getPort();
    }

    protected PublickeyAuthenticator getPublickeyAuthenticator() {
        return (username, key, session) -> true;
    }

    public void tearDown() throws Exception {
        tearDownServer();
    }

    public void tearDownServer() {
        try {
            // stop asap as we may hang forever
            if (sshd != null) {
                sshd.stop(true);
            }
        } catch (Exception e) {
            // ignore while shutting down as we could be polling during
            // shutdown
            // and get errors when the ftp server is stopping. This is only
            // an issue
            // since we host the ftp server embedded in the same jvm for
            // unit testing
            LOG.trace("Exception while shutting down: {}", e.getMessage(), e);
        } finally {
            sshd = null;
        }
    }

    // disconnect all existing SSH sessions to test reconnect functionality
    public void disconnectAllSessions() throws IOException {
        List<AbstractSession> sessions = sshd.getActiveSessions();
        for (AbstractSession session : sessions) {
            session.disconnect(4, "dummy");
        }
    }

    public byte[] buildKnownHosts() {
        return String.format(KNOWN_HOSTS, port).getBytes();
    }

    public String getKnownHostsFile() {
        return knownHosts.toString();
    }

    public Path getFtpRootDir() {
        return rootDir;
    }

    @Override
    protected void registerProperties(BiConsumer<String, String> store) {
        store.accept(FtpProperties.SERVER_HOST, "localhost");
        store.accept(FtpProperties.SERVER_PORT, String.valueOf(port));
        store.accept(FtpProperties.ROOT_DIR, rootDir.toString());
    }

    public int getPort() {
        return port;
    }

    public static boolean hasRequiredAlgorithms() {
        try {
            FileKeyPairProvider provider = new FileKeyPairProvider(Paths.get("src/test/resources/hostkey.pem"));

            provider.loadKeys(null);
            return true;
        } catch (Exception e) {
            String name = System.getProperty("os.name");
            String message = e.getMessage();

            LOG.warn("SunX509 is not available on this platform [{}] Testing is skipped! Real cause: {}", name, message, e);
            return false;
        }
    }
}
