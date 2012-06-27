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
package org.apache.camel.builder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
 */
public class ThreadPoolBuilderTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        ExecutorService someone = Executors.newCachedThreadPool();
        jndi.bind("someonesPool", someone);
        return jndi;
    }

    public void testThreadPoolBuilderDefault() throws Exception {
        ThreadPoolBuilder builder = new ThreadPoolBuilder(context);
        ExecutorService executor = builder.build(this, "myPool");
        assertNotNull(executor);

        assertEquals(false, executor.isShutdown());
        context.stop();
        assertEquals(true, executor.isShutdown());
    }

    public void testThreadPoolBuilderMaxQueueSize() throws Exception {
        ThreadPoolBuilder builder = new ThreadPoolBuilder(context);
        ExecutorService executor = builder.maxQueueSize(2000).build(this, "myPool");
        assertNotNull(executor);

        assertEquals(false, executor.isShutdown());
        context.stop();
        assertEquals(true, executor.isShutdown());
    }

    public void testThreadPoolBuilderMax() throws Exception {
        ThreadPoolBuilder builder = new ThreadPoolBuilder(context);
        ExecutorService executor = builder.maxPoolSize(100).build(this, "myPool");
        assertNotNull(executor);

        assertEquals(false, executor.isShutdown());
        context.stop();
        assertEquals(true, executor.isShutdown());
    }

    public void testThreadPoolBuilderCoreAndMax() throws Exception {
        ThreadPoolBuilder builder = new ThreadPoolBuilder(context);
        ExecutorService executor = builder.poolSize(50).maxPoolSize(100).build(this, "myPool");
        assertNotNull(executor);

        assertEquals(false, executor.isShutdown());
        context.stop();
        assertEquals(true, executor.isShutdown());
    }

    public void testThreadPoolBuilderKeepAlive() throws Exception {
        ThreadPoolBuilder builder = new ThreadPoolBuilder(context);
        ExecutorService executor = builder.keepAliveTime(30).build(this, "myPool");
        assertNotNull(executor);

        assertEquals(false, executor.isShutdown());
        context.stop();
        assertEquals(true, executor.isShutdown());
    }

    public void testThreadPoolBuilderKeepAliveTimeUnit() throws Exception {
        ThreadPoolBuilder builder = new ThreadPoolBuilder(context);
        ExecutorService executor = builder.keepAliveTime(20000, TimeUnit.MILLISECONDS).build(this, "myPool");
        assertNotNull(executor);

        assertEquals(false, executor.isShutdown());
        context.stop();
        assertEquals(true, executor.isShutdown());
    }

    public void testThreadPoolBuilderAll() throws Exception {
        ThreadPoolBuilder builder = new ThreadPoolBuilder(context);
        ExecutorService executor = builder.poolSize(50).maxPoolSize(100).maxQueueSize(2000)
                .keepAliveTime(20000, TimeUnit.MILLISECONDS)
                .rejectedPolicy(ThreadPoolRejectedPolicy.DiscardOldest)
                .build(this, "myPool");
        assertNotNull(executor);

        assertEquals(false, executor.isShutdown());
        context.stop();
        assertEquals(true, executor.isShutdown());
    }

    public void testThreadPoolBuilderTwoPoolsDefault() throws Exception {
        ThreadPoolBuilder builder = new ThreadPoolBuilder(context);
        ExecutorService executor = builder.build(this, "myPool");
        ExecutorService executor2 = builder.build(this, "myOtherPool");

        assertNotNull(executor);
        assertNotNull(executor2);

        assertEquals(false, executor.isShutdown());
        assertEquals(false, executor2.isShutdown());
        context.stop();
        assertEquals(true, executor.isShutdown());
        assertEquals(true, executor2.isShutdown());
    }

    public void testThreadPoolBuilderScheduled() throws Exception {
        ThreadPoolBuilder builder = new ThreadPoolBuilder(context);
        ScheduledExecutorService executor = builder.poolSize(5).maxQueueSize(2000)
                .buildScheduled();
        assertNotNull(executor);

        assertEquals(false, executor.isShutdown());
        context.stop();
        assertEquals(true, executor.isShutdown());
    }

    public void testThreadPoolBuilderScheduledName() throws Exception {
        ThreadPoolBuilder builder = new ThreadPoolBuilder(context);
        ScheduledExecutorService executor = builder.poolSize(5).maxQueueSize(2000)
                .buildScheduled("myScheduledPool");
        assertNotNull(executor);

        assertEquals(false, executor.isShutdown());
        context.stop();
        assertEquals(true, executor.isShutdown());
    }


    public void testThreadPoolBuilderScheduledSourceName() throws Exception {
        ThreadPoolBuilder builder = new ThreadPoolBuilder(context);
        ScheduledExecutorService executor = builder.poolSize(5).maxQueueSize(2000)
                .buildScheduled(this, "myScheduledPool");
        assertNotNull(executor);

        assertEquals(false, executor.isShutdown());
        context.stop();
        assertEquals(true, executor.isShutdown());
    }


}
