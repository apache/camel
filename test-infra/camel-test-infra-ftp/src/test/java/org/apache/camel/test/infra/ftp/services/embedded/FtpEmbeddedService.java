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

package org.apache.camel.test.infra.ftp.services.embedded;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.camel.test.infra.common.services.AbstractTestService;
import org.apache.camel.test.infra.ftp.common.FtpProperties;
import org.apache.camel.test.infra.ftp.services.FtpService;
import org.apache.commons.io.FileUtils;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.impl.DefaultFtpServer;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.listener.Listener;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtpEmbeddedService extends AbstractTestService implements FtpService {
    protected static final String DEFAULT_LISTENER = "default";
    private static final Logger LOG = LoggerFactory.getLogger(FtpEmbeddedService.class);

    protected FtpServer ftpServer;
    protected int port;

    protected Path rootDir;
    private final EmbeddedConfigurationBuilder embeddedConfigurationTemplate;
    private ExtensionContext context;

    public FtpEmbeddedService() {
        this(EmbeddedConfigurationBuilder.defaultConfigurationTemplate());
    }

    protected FtpEmbeddedService(EmbeddedConfigurationBuilder embeddedConfigurationTemplate) {
        this.embeddedConfigurationTemplate = embeddedConfigurationTemplate;
    }

    @Override
    protected void setUp() throws Exception {
        embeddedConfigurationTemplate.withTestDirectory(context.getDisplayName().replace("()", ""));
        EmbeddedConfiguration embeddedConfiguration = embeddedConfigurationTemplate.build();

        rootDir = testDirectory().resolve(embeddedConfiguration.getTestDirectory());
        FileUtils.deleteDirectory(rootDir.toFile());

        FtpServerFactory factory = createFtpServerFactory(embeddedConfiguration);
        ftpServer = factory.createServer();
        ftpServer.start();

        port = getListenerPort();
    }

    private int getListenerPort() {
        return ((DefaultFtpServer) ftpServer).getListeners().values().stream()
                .map(Listener::getPort).findAny().get();
    }

    @Deprecated
    private Path testDirectory() {
        return Paths.get("target", "ftp", context.getRequiredTestClass().getSimpleName());
    }

    protected void createUser(UserManager userMgr, String name, String password, Path home, boolean writePermission) {
        try {
            BaseUser user = new BaseUser();
            user.setName(name);
            user.setPassword(password);
            user.setHomeDirectory(home.toString());
            if (writePermission) {
                user.setAuthorities(Collections.singletonList(new WritePermission()));
            }
            userMgr.save(user);
        } catch (FtpException e) {
            throw new IllegalStateException("Unable to create FTP user", e);
        }
    }

    protected FtpServerFactory createFtpServerFactory(EmbeddedConfiguration embeddedConfiguration) {
        NativeFileSystemFactory fsf = new NativeFileSystemFactory();
        fsf.setCreateHome(true);

        PropertiesUserManagerFactory pumf = new PropertiesUserManagerFactory();
        pumf.setAdminName(embeddedConfiguration.getAdmin().getUsername());
        pumf.setPasswordEncryptor(new ClearTextPasswordEncryptor());
        pumf.setFile(null);

        UserManager userMgr = pumf.createUserManager();
        for (EmbeddedConfiguration.User user : embeddedConfiguration.getUsers()) {
            final EmbeddedConfiguration.User.UserInfo userInfo = user.getUserInfo();
            final Path homeDir = userInfo.getHome() == null ? rootDir : Path.of(userInfo.getHome());

            createUser(userMgr, user.getUsername(), user.getPassword(), homeDir, userInfo.isWritePermission());
        }

        FtpServerFactory serverFactory = new FtpServerFactory();
        serverFactory.setUserManager(userMgr);
        serverFactory.setFileSystem(fsf);
        serverFactory.setConnectionConfig(new ConnectionConfigFactory().createConnectionConfig());

        ListenerFactory factory = new ListenerFactory();
        factory.setPort(port);
        factory.setServerAddress(embeddedConfiguration.getServerAddress());

        final Listener listener = factory.createListener();

        serverFactory.addListener(DEFAULT_LISTENER, listener);

        return serverFactory;
    }

    @Override
    protected void tearDown() {
        try {
            if (ftpServer != null) {
                ftpServer.stop();
            }
        } catch (Exception e) {
            // ignore while shutting down as we could be polling during
            // shutdown and get errors when the ftp server is stopping. This is only
            // an issue since we host the ftp server embedded in the same jvm for
            // unit testing
            LOG.trace("Exception while shutting down: {}", e.getMessage(), e);
        } finally {
            ftpServer = null;
        }
    }

    public void disconnectAllSessions() {
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
    protected void registerProperties(BiConsumer<String, String> store) {
        final String host = ((DefaultFtpServer) ftpServer).getListeners().values().stream()
                .map(Listener::getServerAddress).findAny().get();
        store.accept(FtpProperties.SERVER_HOST, host);
        store.accept(FtpProperties.SERVER_PORT, String.valueOf(getPort()));
        store.accept(FtpProperties.ROOT_DIR, rootDir.toString());
    }

    @Override
    public void registerProperties() {
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.GLOBAL);
        registerProperties(store::put);
    }

    @Override
    public Path getFtpRootDir() {
        return rootDir;
    }

    public void resume() {
        ftpServer.resume();
        port = getListenerPort();
    }

    @Override
    public int getPort() {
        return port;
    }

    public int countConnections() {
        int count = 0;

        // stop all listeners
        Map<String, Listener> listeners = ((DefaultFtpServer) ftpServer).getListeners();
        for (Listener listener : listeners.values()) {
            count += listener.getActiveSessions().size();
        }

        return count;
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        this.context = extensionContext;
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        this.context = null;
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        shutdown();
        this.context = null;
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        this.context = extensionContext;
        initialize();
    }

    public Path ftpFile(String file) {
        return getFtpRootDir().resolve(file);
    }
}
