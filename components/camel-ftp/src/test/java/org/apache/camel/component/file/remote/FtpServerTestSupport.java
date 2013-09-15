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
import java.security.NoSuchAlgorithmException;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.junit.After;
import org.junit.Before;

import static org.apache.camel.language.simple.SimpleLanguage.simple;

/**
 * Base class for unit testing using a FTPServer
 */
public abstract class FtpServerTestSupport extends BaseServerTestSupport {

    protected static final String FTP_ROOT_DIR = "./target/res/home";
    protected static final File USERS_FILE = new File("./src/test/resources/users.properties");
    protected static final String DEFAULT_LISTENER = "default";

    protected FtpServer ftpServer;
    protected boolean canTest;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory(FTP_ROOT_DIR);

        canTest = false;

        FtpServerFactory factory = createFtpServerFactory();
        if (factory != null) {
            ftpServer = factory.createServer();
            if (ftpServer != null) {
                ftpServer.start();
                canTest = true;
            }
        }

        try {
            super.setUp();
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

        if (ftpServer != null) {
            try {
                ftpServer.stop();
                ftpServer = null;
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

    protected FtpServerFactory createFtpServerFactory() throws Exception {
        assertTrue(USERS_FILE.exists());
        assertTrue("Port number is not initialized in an expected range: " + BaseServerTestSupport.port, BaseServerTestSupport.port >= 21000);

        NativeFileSystemFactory fsf = new NativeFileSystemFactory();
        fsf.setCreateHome(true);

        PropertiesUserManagerFactory pumf = new PropertiesUserManagerFactory();
        pumf.setAdminName("admin");
        pumf.setPasswordEncryptor(new ClearTextPasswordEncryptor());
        pumf.setFile(USERS_FILE);
        UserManager userMgr = pumf.createUserManager();
        
        ListenerFactory factory = new ListenerFactory();
        factory.setPort(BaseServerTestSupport.port);
        
        FtpServerFactory serverFactory = new FtpServerFactory();
        serverFactory.setUserManager(userMgr);
        serverFactory.setFileSystem(fsf);
        serverFactory.setConnectionConfig(new ConnectionConfigFactory().createConnectionConfig());
        serverFactory.addListener(DEFAULT_LISTENER, factory.createListener());

        return serverFactory;
    }
    
    public void sendFile(String url, Object body, String fileName) {
        template.sendBodyAndHeader(url, body, Exchange.FILE_NAME, simple(fileName));
    }
    
}
