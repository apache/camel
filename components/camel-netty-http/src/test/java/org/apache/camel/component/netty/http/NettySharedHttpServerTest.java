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
package org.apache.camel.component.netty.http;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultClassResolver;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class NettySharedHttpServerTest extends BaseNettyTest {

    private NettySharedHttpServer nettySharedHttpServer;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        nettySharedHttpServer = new DefaultNettySharedHttpServer();
        nettySharedHttpServer.setClassResolver(new DefaultClassResolver(context));

        NettySharedHttpServerBootstrapConfiguration configuration = new NettySharedHttpServerBootstrapConfiguration();
        configuration.setPort(getPort());
        configuration.setHost("localhost");
        configuration.setBacklog(20);
        configuration.setKeepAlive(true);
        configuration.setCompression(true);
        nettySharedHttpServer.setNettyServerBootstrapConfiguration(configuration);

        nettySharedHttpServer.start();

        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myNettyServer", nettySharedHttpServer);
        return jndi;
    }

    @Override
    public void tearDown() throws Exception {
        nettySharedHttpServer.stop();
        super.tearDown();
    }

    @Test
    public void testTwoRoutes() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello Camel");

        String out = template.requestBody("netty-http:http://localhost:{{port}}/foo", "Hello World", String.class);
        assertEquals("Bye World", out);

        out = template.requestBody("netty-http:http://localhost:{{port}}/bar", "Hello Camel", String.class);
        assertEquals("Bye Camel", out);

        assertEquals(2, nettySharedHttpServer.getConsumersSize());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // we are using a shared netty http server, so the port number is not needed to be defined in the uri
                from("netty-http:http://localhost/foo?nettySharedHttpServer=#myNettyServer")
                    .log("Foo route using thread ${threadName}")
                    .to("mock:foo")
                    .transform().constant("Bye World");

                // we are using a shared netty http server, so the port number is not needed to be defined in the uri
                from("netty-http:http://localhost/bar?nettySharedHttpServer=#myNettyServer")
                    .log("Bar route using thread ${threadName}")
                    .to("mock:bar")
                    .transform().constant("Bye Camel");
            }
        };
    }

}
