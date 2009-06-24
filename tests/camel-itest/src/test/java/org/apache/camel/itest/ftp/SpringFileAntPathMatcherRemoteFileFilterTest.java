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
package org.apache.camel.itest.ftp;

import java.io.File;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.impl.PropertiesUserManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;


/**
 * Unit testing FTP ant path matcher
 */
@ContextConfiguration
public class SpringFileAntPathMatcherRemoteFileFilterTest extends AbstractJUnit4SpringContextTests {
    protected FtpServer ftpServer;

    protected String expectedBody = "Godday World";
    @Autowired
    protected ProducerTemplate template;
    @EndpointInject(name = "myFTPEndpoint")
    protected Endpoint inputFTP;
    @EndpointInject(uri = "mock:result")
    protected MockEndpoint result;

    @Test
    public void testAntPatchMatherFilter() throws Exception {
        result.expectedBodiesReceived(expectedBody);

        template.sendBodyAndHeader(inputFTP, "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(inputFTP, "Bye World", Exchange.FILE_NAME, "bye.xml");
        template.sendBodyAndHeader(inputFTP, "Bad world", Exchange.FILE_NAME, "subfolder/badday.txt");
        template.sendBodyAndHeader(inputFTP, "Day world", Exchange.FILE_NAME, "day.xml");
        template.sendBodyAndHeader(inputFTP, expectedBody, Exchange.FILE_NAME, "subfolder/foo/godday.txt");

        result.assertIsSatisfied();
    }

    @Before
    public void setUp() throws Exception {        
        initFtpServer();
        ftpServer.start();
    }

    @After
    public void tearDown() throws Exception {        
        ftpServer.stop();
        ftpServer = null;
    }

    protected void initFtpServer() throws Exception {
        FtpServerFactory serverFactory = new FtpServerFactory();

        // setup user management to read our users.properties and use clear text passwords
        File file = new File("./src/test/resources/users.properties").getAbsoluteFile();
        UserManager uman = new PropertiesUserManager(new ClearTextPasswordEncryptor(), file, "admin");
        serverFactory.setUserManager(uman);

        NativeFileSystemFactory fsf = new NativeFileSystemFactory();
        fsf.setCreateHome(true);
        serverFactory.setFileSystem(fsf);

        ListenerFactory factory = new ListenerFactory();
        factory.setPort(20123);
        serverFactory.addListener("default", factory.createListener());

        ftpServer = serverFactory.createServer();
    }

}

