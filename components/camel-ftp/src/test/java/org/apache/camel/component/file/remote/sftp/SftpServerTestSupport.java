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
package org.apache.camel.component.file.remote.sftp;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.io.FileUtils;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.scp.ScpCommandFactory;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.createDirectory;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

public class SftpServerTestSupport extends BaseServerTestSupport {

    protected static final String FTP_ROOT_DIR = "target/res/home";
    private static final Logger LOG = LoggerFactory.getLogger(SftpServerTestSupport.class);
    private static final String KNOWN_HOSTS = "[localhost]:%d ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQDdfIWeSV4o68dRrKS"
                                              + "zFd/Bk51E65UTmmSrmW0O1ohtzi6HzsDPjXgCtlTt3FqTcfFfI92IlTr4JWqC9UK1QT1ZTeng0MkPQmv68hDANHbt5CpETZHjW5q4OOgWhV"
                                              + "vj5IyOC2NZHtKlJBkdsMAa15ouOOJLzBvAvbqOR/yUROsEiQ==";
    protected SshServer sshd;
    protected boolean canTest;
    protected String oldUserHome;
    protected boolean rootDirMode;
    private String simulatedUserHome = "./target/user-home";
    private String simulatedUserSsh = "./target/user-home/.ssh";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory(FTP_ROOT_DIR);

        oldUserHome = System.getProperty("user.home");

        System.setProperty("user.home", "target/user-home");

        deleteDirectory(simulatedUserHome);
        createDirectory(simulatedUserHome);
        createDirectory(simulatedUserSsh);

        super.setUp();

        FileUtils.writeByteArrayToFile(new File(simulatedUserSsh + "/known_hosts"), buildKnownHosts());

        setUpServer();
    }

    protected void setUpServer() throws Exception {
        canTest = true;
        try {
            sshd = SshServer.setUpDefaultServer();
            sshd.setPort(getPort());
            sshd.setKeyPairProvider(new FileKeyPairProvider(Paths.get("src/test/resources/hostkey.pem")));
            sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
            sshd.setCommandFactory(new ScpCommandFactory());
            sshd.setPasswordAuthenticator((username, password, session) -> true);
            sshd.setPublickeyAuthenticator(getPublickeyAuthenticator());
            if (rootDirMode) {
                sshd.setFileSystemFactory(new VirtualFileSystemFactory(FileSystems.getDefault().getPath(System.getProperty("user.dir") + "/target/res")));
            }
            sshd.start();
        } catch (Exception e) {
            // ignore if algorithm is not on the OS
            NoSuchAlgorithmException nsae = ObjectHelper.getException(NoSuchAlgorithmException.class, e);
            if (nsae != null) {
                canTest = false;

                String name = System.getProperty("os.name");
                String message = nsae.getMessage();
                LOG.warn("SunX509 is not avail on this platform [{}] Testing is skipped! Real cause: {}", name, message);
            } else {
                // some other error then throw it so the test can fail
                throw e;
            }
        }
    }

    protected PublickeyAuthenticator getPublickeyAuthenticator() {
        return (username, key, session) -> true;
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        if (oldUserHome != null) {
            System.setProperty("user.home", oldUserHome);
        } else {
            System.clearProperty("user.home");
        }

        super.tearDown();

        tearDownServer();
    }

    protected void tearDownServer() {
        if (sshd != null) {
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
            }
        }
    }

    protected boolean canTest() {
        return canTest;
    }

    // disconnect all existing SSH sessions to test reconnect functionality
    protected void disconnectAllSessions() throws IOException {
        List<AbstractSession> sessions = sshd.getActiveSessions();
        for (AbstractSession session : sessions) {
            session.disconnect(4, "dummy");
        }
    }

    protected byte[] buildKnownHosts() {
        return String.format(KNOWN_HOSTS, port).getBytes();
    }

    protected String getKnownHostsFile() {
        return simulatedUserSsh + "/known_hosts";
    }
}
