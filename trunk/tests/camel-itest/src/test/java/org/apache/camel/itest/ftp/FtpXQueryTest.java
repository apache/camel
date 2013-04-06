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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.impl.PropertiesUserManager;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 */
public class FtpXQueryTest extends CamelTestSupport {
    protected static int ftpPort;
    protected FtpServer ftpServer;
    private String ftp = "ftp:localhost:" + ftpPort + "/myapp?password=admin&username=admin";
    
    @BeforeClass
    public static void initPort() throws Exception {
        ftpPort = AvailablePortFinder.getNextAvailable(20127);
    }

    @Test
    public void testXQueryFromFtp() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:davsclaus");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("Hello World");

        MockEndpoint other = getMockEndpoint("mock:other");
        other.expectedMessageCount(1);
        other.message(0).body(String.class).contains("Bye World");

        template.sendBodyAndHeader(ftp, "<mail from=\"davsclaus@apache.org\"><subject>Hey</subject><body>Hello World!</body></mail>",
                Exchange.FILE_NAME, "claus.xml");

        template.sendBodyAndHeader(ftp, "<mail from=\"janstey@apache.org\"><subject>Hey</subject><body>Bye World!</body></mail>",
                Exchange.FILE_NAME, "janstey.xml");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(ftp)
                    .choice()
                        .when().xquery("/mail/@from = 'davsclaus@apache.org'")
                            .to("mock:davsclaus")
                        .otherwise()
                            .to("mock:other");
            }
        };
    }

    public void setUp() throws Exception {
        super.setUp();
        initFtpServer();
        ftpServer.start();
    }

    public void tearDown() throws Exception {
        super.tearDown();
        ftpServer.stop();
        ftpServer = null;
    }

    protected void initFtpServer() throws Exception {
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
