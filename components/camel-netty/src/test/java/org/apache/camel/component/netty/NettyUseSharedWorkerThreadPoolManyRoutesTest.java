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
package org.apache.camel.component.netty;

import io.netty.channel.EventLoopGroup;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NettyUseSharedWorkerThreadPoolManyRoutesTest extends BaseNettyTest {

    private static final Logger LOG = LoggerFactory.getLogger(NettyUseSharedWorkerThreadPoolManyRoutesTest.class);

    @BindToRegistry("sharedWorker")
    private EventLoopGroup sharedBoosGroup = new NettyWorkerPoolBuilder().withWorkerCount(10).build();
    @BindToRegistry("sharedBoss")
    private EventLoopGroup sharedWorkerGroup = new NettyServerBossPoolBuilder().withBossCount(20).build();
    private int before;
    private AvailablePortFinder.Port[] ports;

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        before = Thread.activeCount();
        ports = new AvailablePortFinder.Port[60];
        for (int i = 0; i < ports.length; i++) {
            ports[i] = AvailablePortFinder.find();
        }
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        for (AvailablePortFinder.Port port : ports) {
            port.release();
        }
    }

    @Test
    public void testSharedThreadPool() {
        int delta = Thread.activeCount() - before;

        LOG.info("Created threads {}", delta);
        assertTrue(delta < 50, "There should not be created so many threads: " + delta);

        sharedBoosGroup.shutdownGracefully().awaitUninterruptibly();
        sharedWorkerGroup.shutdownGracefully().awaitUninterruptibly();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                for (AvailablePortFinder.Port port : ports) {
                    from("netty:tcp://localhost:" + port.getPort() + "?textline=true&sync=true&usingExecutorService=false"
                         + "&bossGroup=#sharedBoss&workerGroup=#sharedWorker")
                            .validate(body().isInstanceOf(String.class)).to("log:result").to("mock:result")
                            .transform(body().regexReplaceAll("Hello", "Bye"));
                }
            }
        };
    }
}
