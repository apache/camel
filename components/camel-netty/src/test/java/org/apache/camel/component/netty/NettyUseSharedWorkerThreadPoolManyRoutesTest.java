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
import org.jboss.netty.channel.socket.nio.BossPool;
import org.jboss.netty.channel.socket.nio.WorkerPool;
import org.junit.Test;

/**
 * @version 
 */
public class NettyUseSharedWorkerThreadPoolManyRoutesTest extends BaseNettyTest {

    private JndiRegistry jndi;
    private BossPool sharedBoos;
    private WorkerPool sharedWorker;
    private int before;

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    public void setUp() throws Exception {
        before = Thread.activeCount();
        super.setUp();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        jndi = super.createRegistry();
        return jndi;
    }

    @Test
    public void testSharedThreadPool() throws Exception {
        int delta = Thread.activeCount() - before;

        log.info("Created threads {}", delta);
        assertTrue("There should not be created so many threads: " + delta, delta < 50);

        sharedWorker.shutdown();
        sharedBoos.shutdown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                sharedWorker = new NettyWorkerPoolBuilder().withWorkerCount(10).build();
                jndi.bind("sharedWorker", sharedWorker);
                sharedBoos = new NettyServerBossPoolBuilder().withBossCount(20).build();
                jndi.bind("sharedBoss", sharedBoos);

                for (int i = 0; i < 100; i++) {
                    from("netty:tcp://localhost:" + getNextPort() + "?textline=true&sync=true&orderedThreadPoolExecutor=false"
                            + "&bossPool=#sharedBoss&workerPool=#sharedWorker")
                        .validate(body().isInstanceOf(String.class))
                        .to("log:result")
                        .to("mock:result")
                        .transform(body().regexReplaceAll("Hello", "Bye"));
                }
            }
        };
    }
}