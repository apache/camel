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
package org.apache.camel.component.netty;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

/**
 *
 */
public class UnsharableCodecsConflicts2Test extends BaseNettyTest {

    static final byte[] LENGTH_HEADER = {0x00, 0x00, 0x40, 0x00}; // 16384 bytes

    private Processor processor = new P();
    private int port;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        // create a single decoder
        ChannelHandlerFactory decoder = ChannelHandlerFactories.newLengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4);
        registry.bind("length-decoder", decoder);

        return registry;
    }

    @Test
    public void unsharableCodecsConflictsTest() throws Exception {
        byte[] data1 = new byte[8192];
        byte[] data2 = new byte[16383];
        Arrays.fill(data1, (byte) 0x38);
        Arrays.fill(data2, (byte) 0x39);
        byte[] body1 = (new String(LENGTH_HEADER) + new String(data1)).getBytes();
        byte[] body2 = (new String(LENGTH_HEADER) + new String(data2)).getBytes();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(new String(data2) + "9");

        Socket client1 = getSocket("localhost", port);
        Socket client2 = getSocket("localhost", port);

        // use two clients to send to the same server at the same time
        try {
            sendBuffer(body2, client2);
            sendBuffer(body1, client1);
            sendBuffer(new String("9").getBytes(), client2);
        } catch (Exception e) {
            log.error("", e);
        } finally {
            client1.close();
            client2.close();
        }

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                port = getPort();

                from("netty:tcp://localhost:{{port}}?decoder=#length-decoder&sync=false")
                        .process(processor)
                        .to("mock:result");
            }
        };
    }

    private static Socket getSocket(String host, int port) throws IOException {
        Socket s = new Socket(host, port);
        s.setSoTimeout(60000);
        return s;
    }

    public static void sendBuffer(byte[] buf, Socket server) throws Exception {
        OutputStream netOut = server.getOutputStream();
        OutputStream dataOut = new BufferedOutputStream(netOut);
        try {
            dataOut.write(buf, 0, buf.length);
            dataOut.flush();
        } catch (Exception e) {
            server.close();
            throw e;
        }
    }

    class P implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getOut().setBody(exchange.getIn().getBody(String.class));
        }
    }
}
