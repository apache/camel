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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.ftp.common.FtpProperties;
import org.apache.camel.test.infra.ftp.services.FtpService;
import org.apache.camel.util.FileUtil;
import org.apache.commons.io.FileUtils;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.scp.ScpCommandFactory;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.createDirectory;

public class SftpEmbeddedService implements FtpService {
    private static final Logger LOG = LoggerFactory.getLogger(SftpEmbeddedService.class);
    private static final String FTP_ROOT_DIR = "target/res/home";
    private static final String KNOWN_HOSTS = "[localhost]:%d ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQDdfIWeSV4o68dRrKS"
                                              + "zFd/Bk51E65UTmmSrmW0O1ohtzi6HzsDPjXgCtlTt3FqTcfFfI92IlTr4JWqC9UK1QT1ZTeng0MkPQmv68hDANHbt5CpETZHjW5q4OOgWhV"
                                              + "vj5IyOC2NZHtKlJBkdsMAa15ouOOJLzBvAvbqOR/yUROsEiQ==";

    protected SshServer sshd;
    protected String oldUserHome;
    protected final boolean rootDirMode;
    protected final int port;

    private String simulatedUserHome = "./target/user-home";
    private String simulatedUserSsh = "./target/user-home/.ssh";

    public SftpEmbeddedService() {
        this(false);
    }

    public SftpEmbeddedService(boolean rootDirMode) {
        port = AvailablePortFinder.getNextAvailable();
        this.rootDirMode = rootDirMode;
    }

    public void setUp() throws Exception {
        FileUtil.removeDir(new File(FTP_ROOT_DIR));

        oldUserHome = System.getProperty("user.home");

        System.setProperty("user.home", "target/user-home");

        FileUtil.removeDir(new File(simulatedUserHome));
        createDirectory(simulatedUserHome);
        createDirectory(simulatedUserSsh);

        FileUtils.writeByteArrayToFile(new File(simulatedUserSsh + "/known_hosts"), buildKnownHosts());

        setUpServer();
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
            sshd.setFileSystemFactory(new VirtualFileSystemFactory(
                    FileSystems.getDefault().getPath(System.getProperty("user.dir") + "/target/res")));
        }

        sshd.start();
    }

    protected PublickeyAuthenticator getPublickeyAuthenticator() {
        return (username, key, session) -> true;
    }

    public void tearDown() throws Exception {
        if (oldUserHome != null) {
            System.setProperty("user.home", oldUserHome);
        } else {
            System.clearProperty("user.home");
        }

        tearDownServer();
    }

    public void tearDownServer() {
        if (sshd == null) {
            return;
        }

        try {
            // stop asap as we may hang forever
            sshd.stop(true);

            sshd = null;
        } catch (Exception e) {
            // ignore while shutting down as we could be polling during
            // shutdown
            // and get errors when the ftp server is stopping. This is only
            // an issue
            // since we host the ftp server embedded in the same jvm for
            // unit testing
            LOG.trace("Exception while shutting down: {}", e.getMessage(), e);
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
        return simulatedUserSsh + "/known_hosts";
    }

    public static String getFtpRootDir() {
        return FTP_ROOT_DIR;
    }

    @Override
    public void registerProperties() {
        System.setProperty(FtpProperties.SERVER_HOST, "localhost");
        System.setProperty(FtpProperties.SERVER_PORT, String.valueOf(port));
        System.setProperty(FtpProperties.ROOT_DIR, FTP_ROOT_DIR);
    }

    @Override
    public void initialize() {
        try {
            setUp();

            registerProperties();
        } catch (Exception e) {
            Assertions.fail("Unable to initialize the SFTP server " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        try {
            tearDown();
        } catch (Exception e) {
            Assertions.fail("Unable to shutdown the SFTP server " + e.getMessage());
        }
    }

    public int getPort() {
        return port;
    }

    public static boolean hasRequiredAlgorithms() {
        try {
            FileKeyPairProvider provider = new FileKeyPairProvider(Paths.get("src/test/resources/hostkey.pem"));

            provider.loadKeys();
            return true;
        } catch (Exception e) {
            String name = System.getProperty("os.name");
            String message = e.getMessage();

            LOG.warn("SunX509 is not available on this platform [{}] Testing is skipped! Real cause: {}", name, message, e);
            return false;
        }
    }
}
