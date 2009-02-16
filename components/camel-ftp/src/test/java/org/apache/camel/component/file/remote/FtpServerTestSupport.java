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
import java.io.FileOutputStream;
import java.util.Random;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.converter.IOConverter;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.impl.PropertiesUserManager;

/**
 * Base class for unit testing using a FTPServer
 */
public abstract class FtpServerTestSupport extends ContextTestSupport {

    public static final String FTP_ROOT_DIR = "./res/home/";

    protected FtpServer ftpServer;

    private int port;

    public int getPort() {
        return port;
    }

    protected void setUp() throws Exception {
        initPort();
        super.setUp();
        initFtpServer();
        ftpServer.start();
    }

    protected void tearDown() throws Exception {
        try {
            super.tearDown();
            ftpServer.stop();
            ftpServer = null;
            port = 0;
        } catch (Exception e) {
            // ignore while shutting down as we could be polling during shutdown
            // and get errors when the ftp server is stopping. This is only an issue
            // since we host the ftp server embedded in the same jvm for unit testing
        }
    }

    protected void initFtpServer() throws Exception {
        if (port < 21000) {
            throw new IllegalArgumentException("Port number is not initialized in an expected range: " + getPort());
        }

        FtpServerFactory serverFactory = new FtpServerFactory();

        // setup user management to read our users.properties and use clear text passwords
        File file = new File("./src/test/resources/users.properties").getAbsoluteFile();
        UserManager uman = new PropertiesUserManager(new ClearTextPasswordEncryptor(), file, "admin");
        serverFactory.setUserManager(uman);

        NativeFileSystemFactory fsf = new NativeFileSystemFactory();
        fsf.setCreateHome(true);
        serverFactory.setFileSystem(fsf);

        ListenerFactory factory = new ListenerFactory();
        factory.setPort(port);
        serverFactory.addListener("default", factory.createListener());

        ftpServer = serverFactory.createServer();
    }

    protected void initPort() throws Exception {
        File file = new File("./target/ftpport.txt");
        file = file.getAbsoluteFile();

        if (!file.exists()) {
            // start from somewhere in the 21xxx range
            port = 21000 + new Random().nextInt(900);
        } else {
            // read port number from file
            String s = IOConverter.toString(file);
            port = Integer.parseInt(s);
            // use next number
            port++;
        }

        // save to file, do not append
        FileOutputStream fos = new FileOutputStream(file, false);
        try {
            fos.write(("" + getPort()).getBytes());
        } finally {
            fos.close();
        }
    }
    
    public void sendFile(String url, Object body, String fileName) {
        template.sendBodyAndHeader(url, body, FileComponent.HEADER_FILE_NAME, fileName);
    }
}
