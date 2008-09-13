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
package org.apache.camel.component.file.remote;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.PropertiesUserManager;

/**
 * Base class for unit testing using a FTPServer
 */
public abstract class FtpServerTestSupport extends ContextTestSupport {
    protected FtpServer ftpServer;

    public abstract int getPort();

    protected void setUp() throws Exception {
        super.setUp();
        initFtpServer();
        if (ftpServer.isStopped()) {
            ftpServer.start();
        }
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        if (!ftpServer.isStopped()) {
            ftpServer.getServerContext().dispose();
            ftpServer.stop();
        }
    }

    protected void initFtpServer() throws Exception {
        ftpServer = new FtpServer();

        // setup user management to read our users.properties and use clear text passwords
        PropertiesUserManager uman = new PropertiesUserManager();
        uman.setFile(new File("./src/test/resources/users.properties").getAbsoluteFile());
        uman.setPasswordEncryptor(new ClearTextPasswordEncryptor());
        uman.setAdminName("admin");
        uman.configure();
        ftpServer.setUserManager(uman);

        ftpServer.getListener("default").setPort(getPort());
    }
}
