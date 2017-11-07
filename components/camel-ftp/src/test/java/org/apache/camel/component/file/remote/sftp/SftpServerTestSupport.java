/**
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
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.io.FileUtils;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.scp.ScpCommandFactory;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.junit.After;
import org.junit.Before;

/**
 * @version 
 */
public class SftpServerTestSupport extends BaseServerTestSupport {

    protected static final String FTP_ROOT_DIR = "target/res/home";
    protected SshServer sshd;
    protected boolean canTest;
    protected String oldUserHome;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory(FTP_ROOT_DIR);

        oldUserHome = System.getProperty("user.home");

        System.setProperty("user.home", "target/user-home");

        String simulatedUserHome = "target/user-home";
        String simulatedUserSsh = "target/user-home/.ssh";
        deleteDirectory(simulatedUserHome);
        createDirectory(simulatedUserHome);
        createDirectory(simulatedUserSsh);

        FileUtils.copyInputStreamToFile(getClass().getClassLoader().getResourceAsStream("known_hosts"), new File(simulatedUserSsh + "/known_hosts"));

        super.setUp();

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
            sshd.setPublickeyAuthenticator((username, password, session) -> true);
            sshd.start();
        } catch (Exception e) {
            // ignore if algorithm is not on the OS
            NoSuchAlgorithmException nsae = ObjectHelper.getException(NoSuchAlgorithmException.class, e);
            if (nsae != null) {
                canTest = false;

                String name = System.getProperty("os.name");
                String message = nsae.getMessage();
                log.warn("SunX509 is not avail on this platform [{}] Testing is skipped! Real cause: {}", name, message);
            } else {
                // some other error then throw it so the test can fail
                throw e;
            }
        }
    }

    @Override
    @After
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
                // ignore while shutting down as we could be polling during shutdown
                // and get errors when the ftp server is stopping. This is only an issue
                // since we host the ftp server embedded in the same jvm for unit testing
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
}
