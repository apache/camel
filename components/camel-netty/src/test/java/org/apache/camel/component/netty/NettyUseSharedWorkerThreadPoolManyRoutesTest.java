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
import org.junit.Before;
import org.junit.Test;

public class NettyUseSharedWorkerThreadPoolManyRoutesTest extends BaseNettyTest {

    @BindToRegistry("sharedWorker")
    private EventLoopGroup sharedBoosGroup = new NettyWorkerPoolBuilder().withWorkerCount(10).build();
    @BindToRegistry("sharedBoss")
    private EventLoopGroup sharedWorkerGroup = new NettyServerBossPoolBuilder().withBossCount(20).build();
    private int before;

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        before = Thread.activeCount();
        super.setUp();
    }

    @Test
    public void testSharedThreadPool() throws Exception {
        int delta = Thread.activeCount() - before;

        log.info("Created threads {}", delta);
        assertTrue("There should not be created so many threads: " + delta, delta < 50);

        sharedBoosGroup.shutdownGracefully().awaitUninterruptibly();
        sharedWorkerGroup.shutdownGracefully().awaitUninterruptibly();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                for (int i = 0; i < 60; i++) {
                    from("netty:tcp://localhost:" + getNextPort() + "?textline=true&sync=true&usingExecutorService=false" + "&bossGroup=#sharedBoss&workerGroup=#sharedWorker")
                        .validate(body().isInstanceOf(String.class)).to("log:result").to("mock:result").transform(body().regexReplaceAll("Hello", "Bye"));
                }
            }
        };
    }
}
