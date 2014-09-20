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
package org.apache.camel.component.syslog;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SyslogSpringMinaTest extends CamelSpringTestSupport {

    private static int serverPort;
    private final int messageCount = 1;
    private final String message = "<165>Aug  4 05:34:00 mymachine myproc[10]: %% It's\n         time to make the do-nuts.  %%  Ingredients: Mix=OK, Jelly=OK #\n"
                                   + "         Devices: Mixer=OK, Jelly_Injector=OK, Frier=OK # Transport:\n" + "         Conveyer1=OK, Conveyer2=OK # %%";

    @BeforeClass
    public static void initPort() {
        serverPort = AvailablePortFinder.getNextAvailable(3000);
        System.setProperty("server-port", new Integer(serverPort).toString());
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/syslog/applicationContext-Mina.xml");
    }

    @Test
    public void testSendingRawUDP() throws IOException, InterruptedException {

        MockEndpoint mock = getMockEndpoint("mock:stop1");
        MockEndpoint mock2 = getMockEndpoint("mock:stop2");
        mock.expectedMessageCount(1);
        mock2.expectedMessageCount(1);
        mock2.expectedBodiesReceived(message);

        DatagramSocket socket = new DatagramSocket();
        try {
            InetAddress address = InetAddress.getByName("localhost");
            for (int i = 0; i < messageCount; i++) {

                byte[] data = message.getBytes();

                DatagramPacket packet = new DatagramPacket(data, data.length, address, serverPort);
                socket.send(packet);
                Thread.sleep(100);
            }
        } finally {
            socket.close();
        }

        assertMockEndpointsSatisfied();
    }
}
