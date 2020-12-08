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
import java.util.Map;
import java.util.Set;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.ftp.common.FtpProperties;
import org.apache.camel.test.infra.ftp.services.FtpService;
import org.apache.camel.util.FileUtil;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.impl.DefaultFtpServer;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.listener.Listener;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtpEmbeddedService implements FtpService {
    protected static final String DEFAULT_LISTENER = "default";

    private static final Logger LOG = LoggerFactory.getLogger(FtpEmbeddedService.class);
    private static final File USERS_FILE = new File("./src/test/resources/users.properties");
    private static final String FTP_ROOT_DIR = "./target/res/home";

    protected FtpServer ftpServer;
    protected int port;

    public FtpEmbeddedService() {
        port = AvailablePortFinder.getNextAvailable();
    }

    public void setUp() throws Exception {
        FileUtil.removeDir(new File(FTP_ROOT_DIR));

        FtpServerFactory factory = createFtpServerFactory();
        ftpServer = factory.createServer();
        ftpServer.start();
    }

    protected FtpServerFactory createFtpServerFactory() {
        NativeFileSystemFactory fsf = new NativeFileSystemFactory();
        fsf.setCreateHome(true);

        PropertiesUserManagerFactory pumf = new PropertiesUserManagerFactory();
        pumf.setAdminName("admin");
        pumf.setPasswordEncryptor(new ClearTextPasswordEncryptor());
        pumf.setFile(USERS_FILE);
        UserManager userMgr = pumf.createUserManager();

        ListenerFactory factory = new ListenerFactory();
        factory.setPort(port);

        FtpServerFactory serverFactory = new FtpServerFactory();
        serverFactory.setUserManager(userMgr);
        serverFactory.setFileSystem(fsf);
        serverFactory.setConnectionConfig(new ConnectionConfigFactory().createConnectionConfig());
        serverFactory.addListener(DEFAULT_LISTENER, factory.createListener());

        return serverFactory;
    }

    public void tearDown() throws Exception {
        if (ftpServer == null) {
            return;
        }

        try {
            ftpServer.stop();
            ftpServer = null;
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

    public void disconnectAllSessions() throws IOException {
        // stop all listeners
        Map<String, Listener> listeners = ((DefaultFtpServer) ftpServer).getListeners();
        for (Listener listener : listeners.values()) {
            Set<FtpIoSession> sessions = listener.getActiveSessions();
            for (FtpIoSession session : sessions) {
                session.closeNow();
            }
        }
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
            Assertions.fail("Unable to initialize the FTP server " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        try {
            tearDown();
        } catch (Exception e) {
            Assertions.fail("Unable to terminate the FTP server " + e.getMessage());
        }
    }

    public static String getFtpRootDir() {
        return FTP_ROOT_DIR;
    }

    public void suspend() {
        ftpServer.suspend();
    }

    public void resume() {
        ftpServer.resume();
    }

    public int getPort() {
        return port;
    }
}
