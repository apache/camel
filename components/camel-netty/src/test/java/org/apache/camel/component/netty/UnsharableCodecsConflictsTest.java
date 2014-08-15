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
import java.net.Socket;
import java.util.Arrays;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.IOHelper;
import org.junit.Test;

/**
 *
 */
public class UnsharableCodecsConflictsTest extends BaseNettyTest {

    static final byte[] LENGTH_HEADER = {0x00, 0x00, 0x40, 0x00}; // 4096 bytes

    private Processor processor = new P();

    private int port1;
    private int port2;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        // we can share the decoder between multiple netty consumers, because they have the same configuration
        // and we use a ChannelHandlerFactory
        ChannelHandlerFactory decoder = ChannelHandlerFactories.newLengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4);
        registry.bind("length-decoder", decoder);
        registry.bind("length-decoder2", decoder);

        return registry;
    }

    @Test
    public void canSupplyMultipleCodecsToEndpointPipeline() throws Exception {
        byte[] sPort1 = new byte[8192];
        byte[] sPort2 = new byte[16383];
        Arrays.fill(sPort1, (byte) 0x38);
        Arrays.fill(sPort2, (byte) 0x39);
        byte[] bodyPort1 = (new String(LENGTH_HEADER) + new String(sPort1)).getBytes();
        byte[] bodyPort2 = (new String(LENGTH_HEADER) + new String(sPort2)).getBytes();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(new String(sPort2) + "9");

        Socket server1 = getSocket("localhost", port1);
        Socket server2 = getSocket("localhost", port2);

        try {
            sendSopBuffer(bodyPort2, server2);
            sendSopBuffer(bodyPort1, server1);
            sendSopBuffer(new String("9").getBytes(), server2);
        } catch (Exception e) {
            log.error("", e);
        } finally {
            server1.close();
            server2.close();
        }

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                port1 = getPort();
                port2 = getNextPort();

                from("netty:tcp://localhost:" + port1 + "?decoder=#length-decoder&sync=false")
                        .process(processor);

                from("netty:tcp://localhost:" + port2 + "?decoder=#length-decoder2&sync=false")
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

    public static void sendSopBuffer(byte[] buf, Socket server) throws Exception {
        BufferedOutputStream dataOut = IOHelper.buffered(server.getOutputStream());
        try {
            dataOut.write(buf, 0, buf.length);
            dataOut.flush();
        } catch (Exception e) {
            IOHelper.close(dataOut);
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
