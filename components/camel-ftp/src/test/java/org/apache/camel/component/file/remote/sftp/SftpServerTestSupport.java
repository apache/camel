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

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;

import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.junit.After;
import org.junit.Before;

/**
 * @version 
 */
public class SftpServerTestSupport extends BaseServerTestSupport {

    protected static final String FTP_ROOT_DIR = "target/res/home";
    protected SshServer sshd;
    protected boolean canTest;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory(FTP_ROOT_DIR);
        super.setUp();

        setUpServer();
    }

    protected void setUpServer() throws Exception {
        canTest = true;
        try {
            sshd = SshServer.setUpDefaultServer();
            sshd.setPort(getPort());
            sshd.setKeyPairProvider(new FileKeyPairProvider(new String[]{"src/test/resources/hostkey.pem"}));
            sshd.setSubsystemFactories(Arrays.<NamedFactory<Command>>asList(new SftpSubsystem.Factory()));
            sshd.setCommandFactory(new ScpCommandFactory());
            sshd.setPasswordAuthenticator(new MyPasswordAuthenticator());
            PublickeyAuthenticator publickeyAuthenticator = new PublickeyAuthenticator() {
                // consider all keys as authorized for all users
                @Override
                public boolean authenticate(String username, PublicKey key, ServerSession session) {
                    return true;
                }
            };
            sshd.setPublickeyAuthenticator(publickeyAuthenticator);
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
}
