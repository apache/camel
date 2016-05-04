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

import java.net.InetSocketAddress;
import java.nio.charset.Charset;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.junit.Test;

public class MinaClientModeTcpTextlineDelimiterTest extends BaseMinaTest {

    @Test
    public void testMinaRoute() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        Object body = "Hello there!";
        endpoint.expectedBodiesReceived(body);
        // need to start the server first
        Server server = new Server(getPort());
        server.startup();
        // start the camel route to connect to the server
        context.startRoute("minaRoute");
        endpoint.assertIsSatisfied();
        server.shutdown();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                from(String.format("mina:tcp://127.0.0.1:%1$s?sync=false&textline=true&textlineDelimiter=UNIX&clientMode=true", getPort()))
                    .id("minaRoute")
                    .noAutoStartup()
                    .to("log:before?showAll=true")
                    .to("mock:result")
                    .to("log:after?showAll=true");
            }
        };
    }
    
    private class Server {
        private final int port;
        private IoAcceptor acceptor;

        Server(int port) {
            this.port = port;
        }
        
        public void startup() throws Exception {
            acceptor = new SocketAcceptor();
            TextLineCodecFactory codecFactory = new TextLineCodecFactory(Charset.forName("UTF-8"), LineDelimiter.UNIX);
            acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(codecFactory));
            acceptor.bind(new InetSocketAddress("127.0.0.1", port), new ServerHandler());
        }
        
        public void shutdown() throws Exception {
            acceptor.unbindAll();
        }
            
    }
    
    private class ServerHandler extends IoHandlerAdapter {
        public void sessionOpened(IoSession session) throws Exception {
            session.write("Hello there!\n");
            session.close();
        }
    }
}