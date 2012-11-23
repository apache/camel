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
package org.apache.camel.itest.quartz;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.routepolicy.quartz.CronScheduledRoutePolicy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.impl.PropertiesUserManager;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
@Ignore("Manual test")
public class FtpCronScheduledRoutePolicyTest extends CamelTestSupport {

    protected FtpServer ftpServer;
    private String ftp = "ftp:localhost:20128/myapp?password=admin&username=admin&delay=5s&idempotent=false&localWorkDirectory=target/tmp";

    @Test
    public void testFtpCronScheduledRoutePolicyTest() throws Exception {
        template.sendBodyAndHeader("file:res/home/myapp", "Hello World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(10 * 1000 * 60);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy();
                policy.setRouteStartTime("* 0/2 * * * ?");
                policy.setRouteStopTime("* 1/2 * * * ?");
                policy.setRouteStopGracePeriod(250);
                policy.setTimeUnit(TimeUnit.SECONDS);

                from(ftp)
                    .noAutoStartup().routePolicy(policy).shutdownRunningTask(ShutdownRunningTask.CompleteAllTasks)
                    .log("Processing ${file:name}")
                    .to("log:done");
            }
        };
    }

    public void setUp() throws Exception {
        super.setUp();
        deleteDirectory("res");
        createDirectory("res/home/myapp");
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
        factory.setPort(20128);
        serverFactory.addListener("default", factory.createListener());

        ftpServer = serverFactory.createServer();
    }

}
