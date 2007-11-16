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
package org.apache.camel.component.mina;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class MinaUdpTest extends ContextTestSupport {
    private static final transient Log LOG = LogFactory.getLog(MinaUdpTest.class);
    protected int messageCount = 3;
    protected Thread readerThread;
    protected int port = 4445;
    protected boolean consume = false;

    public void testMinaRoute() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedMessageCount(messageCount);
        endpoint.expectedBodiesReceived("Hello Message: 0", "Hello Message: 1", "Hello Message: 2");

        Thread.sleep(1000);
        sendUdpMessages();

        assertMockEndpointsSatisifed();
        List<Exchange> list = endpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        Object body = exchange.getIn().getBody();
        LOG.debug("Type: " + body.getClass().getName() + " value: " + body);
        LOG.debug("String value: " + exchange.getIn().getBody(String.class));
    }

    @Override
    protected void setUp() throws Exception {

        super.setUp();

        if (consume) {
            final DatagramSocket socket = new DatagramSocket(port);

            readerThread = new Thread() {
                public void run() {
                    try {
                        byte[] buffer = new byte[1024];
                        DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);
                        System.out.println("starting to receive udp packets");
                        while (true) {
                            //incoming.setLength(buffer.length);
                            socket.receive(incoming);
                            byte[] data = incoming.getData();
                            System.out.println("Got data! " + data.length);

                            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data));
                            Object value = in.readObject();
                            System.out.println("Value: " + value);
                        }
                    }
                    catch (Throwable ex) {
                        System.err.println(ex);
                        ex.printStackTrace();
                    }
                }
            };
            readerThread.start();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    protected void sendUdpMessages() throws Exception {
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName("127.0.0.1");
        for (int i = 0; i < messageCount; i++) {
/*
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(buffer);
            out.writeObject("Hello Message: " + i);
            out.close();

            byte[] data = buffer.toByteArray();
*/

            String text = "Hello Message: " + i;
            byte[] data = text.getBytes();

            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);
            Thread.sleep(1000);
        }
        System.out.println("Sent " + messageCount + " messages");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("mina:udp://127.0.0.1:" + port).to("mock:result");
            }
        };
    }
}