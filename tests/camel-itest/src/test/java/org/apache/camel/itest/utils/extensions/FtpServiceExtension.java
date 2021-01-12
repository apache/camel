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

package org.apache.camel.itest.utils.extensions;

import java.io.File;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.impl.PropertiesUserManager;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class FtpServiceExtension implements BeforeAllCallback, AfterAllCallback {
    private static int ftpPort;
    private static FtpServer ftpServer;

    public FtpServiceExtension() {
        if (ftpPort == 0) {
            ftpPort = AvailablePortFinder.getNextAvailable();
        }
    }

    public FtpServiceExtension(String property) {
        if (ftpPort == 0) {
            ftpPort = AvailablePortFinder.getNextAvailable();
        }

        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts
        System.setProperty(property, Integer.toString(ftpPort));
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        // Must keep it running (ie.: do not call ftpServer.stop()), otherwise throws:
        // java.lang.IllegalStateException: FtpServer has been stopped. Restart is not supported
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        if (ftpServer == null) {
            initFtpServer();

            // Must start only once, otherwise throws:
            // java.lang.IllegalStateException: Listener already started
            ftpServer.start();
        }
    }

    public int getPort() {
        return ftpPort;
    }

    public String getAddress() {
        return "ftp:localhost:" + ftpPort + "/myapp?password=admin&username=admin";
    }

    private void initFtpServer() {
        FtpServerFactory serverFactory = new FtpServerFactory();

        // setup user management to read our users.properties and use clear text passwords
        File file = new File("src/test/resources/users.properties");
        UserManager uman = new PropertiesUserManager(new ClearTextPasswordEncryptor(), file, "admin");
        serverFactory.setUserManager(uman);

        NativeFileSystemFactory fsf = new NativeFileSystemFactory();
        fsf.setCreateHome(true);
        serverFactory.setFileSystem(fsf);

        ListenerFactory factory = new ListenerFactory();
        factory.setPort(ftpPort);
        serverFactory.addListener("default", factory.createListener());

        ftpServer = serverFactory.createServer();
    }
}
