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
package org.apache.camel.example;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.example.server.Multiplier;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringJmsClientRemotingServerTest extends TestSupport {
    
    private static ClassPathXmlApplicationContext appCtx;

    @BeforeClass
    public static void setupFreePort() throws Exception {
        // find a free port number, and write that in the custom.properties file
        // which we will use for the unit tests, to avoid port number in use problems
        int port = AvailablePortFinder.getNextAvailable();
        String bank1 = "tcp.port=" + port;

        File custom = new File("target/custom.properties");
        FileOutputStream fos = new FileOutputStream(custom);
        fos.write(bank1.getBytes());
        fos.close();

        appCtx = new ClassPathXmlApplicationContext("/META-INF/spring/camel-server.xml", "camel-client-remoting.xml");
        appCtx.start();
    }

    @AfterClass
    public static void stopSpring() {
        appCtx.stop();
    }

    @Test
    public void testCamelRemotingInvocation() {
        // just get the proxy to the service and we as the client can use the "proxy" as it was
        // a local object we are invoking. Camel will under the covers do the remote communication
        // to the remote ActiveMQ server and fetch the response.
        Multiplier multiplier = appCtx.getBean("multiplierProxy", Multiplier.class);
       
        int response = multiplier.multiply(33);
        assertEquals("Get a wrong response", 99, response);
    }

}
