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
package org.apache.camel.component.scp;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.scp.ScpCommandFactory;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ScpServerTestSupport extends CamelTestSupport {
    protected static final Logger LOG = LoggerFactory.getLogger(ScpServerTestSupport.class);
    protected static final String SCP_ROOT_DIR = "target/test-classes/scp";
    protected static final String KNOWN_HOSTS = "known_hosts";
    protected static int port;

    private boolean acceptLocalhostConnections = true;
    private String knownHostsFile;

    private boolean setupComplete;
    private SshServer sshd;

    protected ScpServerTestSupport() {
        this(true);
    }

    protected ScpServerTestSupport(boolean acceptLocalhostConnections) {
        this.acceptLocalhostConnections = acceptLocalhostConnections;
    }

    protected int getPort() {
        return port;
    }

    protected SshServer getSshd() {
        return sshd;
    }

    @BeforeClass
    public static void initPort() throws Exception {
        port = AvailablePortFinder.getNextAvailable(21000);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory(getScpPath());
        createDirectory(getScpPath());

        setupComplete = startSshd();
        setupKnownHosts();
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();

        if (sshd != null) {
            try {
                sshd.stop(true);
                sshd = null;
            } catch (Exception e) {
                // ignore while shutting down as we could be polling during shutdown
                // and get errors when the ssh server is stopping.
            }
        }
        deleteDirectory(getScpPath());
    }

    protected final String getScpPath() {
        // use this convention and use separate directories for tests
        // (easier to debug and avoid interference)
        return SCP_ROOT_DIR + "/" + getClass().getSimpleName();
    }

    protected String getScpUri() {
        return "scp://localhost:" + getPort() + "/" + getScpPath();
    }

    protected boolean startSshd() {
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(getPort());
        sshd.setKeyPairProvider(new FileKeyPairProvider(Paths.get("src/test/resources/hostkey.pem")));
        sshd.setSubsystemFactories(Arrays.asList(new SftpSubsystemFactory()));
        sshd.setCommandFactory(new ScpCommandFactory());
        sshd.setPasswordAuthenticator(new PasswordAuthenticator() {
            @Override
            public boolean authenticate(String username, String password, ServerSession session) {
                // dummy authentication: allow any user whose password is the same as the username
                return username != null && username.equals(password);
            }
        });
        sshd.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            @Override
            public boolean authenticate(String username, PublicKey key, ServerSession session) {
                return true;
            }
        });
        try {
            sshd.start();
            return true;
        } catch (IOException e) {
            LOG.info("Failed to start ssh server.", e);
        }
        return false;
    }
    
    protected void setupKnownHosts() {
        knownHostsFile = SCP_ROOT_DIR + "/" + KNOWN_HOSTS;
        if (!acceptLocalhostConnections) {
            return;
        }

        // For security reasons (avoiding man in the middle attacks),
        // camel-jsch will only connect to known hosts. For unit testing
        // we use a known key, but since the port is dynamic, the 
        // known_hosts file will be generated by the following code and 
        // should contain a line like below (if 
        // "HashKnownHosts"=="yes" the hostname:port part will be 
        // hashed and look a bit more complicated).
        //
        // [localhost]:21000 ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQDd \
        // fIWeSV4o68dRrKSzFd/Bk51E65UTmmSrmW0O1ohtzi6HzsDPjXgCtlTt3F \
        // qTcfFfI92IlTr4JWqC9UK1QT1ZTeng0MkPQmv68hDANHbt5CpETZHjW5q4 \
        // OOgWhVvj5IyOC2NZHtKlJBkdsMAa15ouOOJLzBvAvbqOR/yUROsEiQ==

        JSch jsch = new JSch();
        try {
            LOG.debug("Using '{}' for known hosts.", knownHostsFile);
            jsch.setKnownHosts(knownHostsFile);
            Session s = jsch.getSession("admin", "localhost", getPort());
            s.setConfig("StrictHostKeyChecking",  "ask");

            // TODO: by the current jsch (0.1.51) setting "HashKnownHosts" to "no" is a workaround
            // to make the tests run green, see also http://sourceforge.net/p/jsch/bugs/63/
            s.setConfig("HashKnownHosts",  "no");
            s.setUserInfo(new UserInfo() {
                @Override
                public String getPassphrase() {
                    return null;
                }
                @Override
                public String getPassword() {
                    return "admin";
                }
                @Override
                public boolean promptPassword(String message) {
                    return true;
                }
                @Override
                public boolean promptPassphrase(String message) {
                    return false;
                }
                @Override
                public boolean promptYesNo(String message) {
                    // accept host authenticity
                    return true;
                }
                @Override
                public void showMessage(String message) {
                }
            });
            // in the process of connecting, "[localhost]:<port>" is added to the knownHostsFile
            s.connect();
            s.disconnect();
        } catch (JSchException e) {
            LOG.info("Could not add [localhost] to known hosts", e);
        }
    }

    public String getKnownHostsFile() {
        return knownHostsFile;
    }

    public boolean isSetupComplete() {
        return setupComplete;
    }

    protected static void traceSecurityProviders() {
        for (Provider p : Security.getProviders()) {
            for (Service s : p.getServices()) {
                LOG.trace("Security provider {} for '{}' algorithm", s.getClassName(), s.getAlgorithm());
            }
        }
    }
}
