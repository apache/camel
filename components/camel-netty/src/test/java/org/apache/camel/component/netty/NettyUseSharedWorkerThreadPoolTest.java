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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.jboss.netty.channel.socket.nio.WorkerPool;
import org.junit.Test;

/**
 * @version 
 */
public class NettyUseSharedWorkerThreadPoolTest extends BaseNettyTest {

    private JndiRegistry jndi;
    private WorkerPool sharedServer;
    private WorkerPool sharedClient;
    private int port;
    private int port2;
    private int port3;

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        jndi = super.createRegistry();
        return jndi;
    }

    @Test
    public void testSharedThreadPool() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(30);

        for (int i = 0; i < 10; i++) {
            String reply = template.requestBody("netty:tcp://localhost:" + port + "?textline=true&sync=true&workerPool=#sharedClientPool", "Hello World", String.class);
            assertEquals("Bye World", reply);

            reply = template.requestBody("netty:tcp://localhost:" + port2 + "?textline=true&sync=true&workerPool=#sharedClientPool", "Hello Camel", String.class);
            assertEquals("Hi Camel", reply);

            reply = template.requestBody("netty:tcp://localhost:" + port3 + "?textline=true&sync=true&workerPool=#sharedClientPool", "Hello Claus", String.class);
            assertEquals("Hej Claus", reply);
        }

        assertMockEndpointsSatisfied();

        sharedServer.shutdown();
        sharedClient.shutdown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // we have 3 routes, but lets try to have only 2 threads in the pool
                sharedServer = new NettyWorkerPoolBuilder().withWorkerCount(2).withName("NettyServer").build();
                jndi.bind("sharedServerPool", sharedServer);
                sharedClient = new NettyWorkerPoolBuilder().withWorkerCount(3).withName("NettyClient").build();
                jndi.bind("sharedClientPool", sharedClient);

                port = getPort();
                port2 = getNextPort();
                port3 = getNextPort();

                from("netty:tcp://localhost:" + port + "?textline=true&sync=true&workerPool=#sharedServerPool&orderedThreadPoolExecutor=false")
                    .validate(body().isInstanceOf(String.class))
                    .to("log:result")
                    .to("mock:result")
                    .transform(body().regexReplaceAll("Hello", "Bye"));

                from("netty:tcp://localhost:" + port2 + "?textline=true&sync=true&workerPool=#sharedServerPool&orderedThreadPoolExecutor=false")
                    .validate(body().isInstanceOf(String.class))
                    .to("log:result")
                    .to("mock:result")
                    .transform(body().regexReplaceAll("Hello", "Hi"));

                from("netty:tcp://localhost:" + port3 + "?textline=true&sync=true&workerPool=#sharedServerPool&orderedThreadPoolExecutor=false")
                    .validate(body().isInstanceOf(String.class))
                    .to("log:result")
                    .to("mock:result")
                    .transform(body().regexReplaceAll("Hello", "Hej"));
            }
        };
    }
}