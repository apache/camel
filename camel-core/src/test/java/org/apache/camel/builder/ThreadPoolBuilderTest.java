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
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spi.ThreadPoolProfile;

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
    
    private void getAndShutdown(ThreadPoolProfile profile) throws Exception {
        ExecutorService executor = context.getExecutorServiceManager().getExecutorService(profile, this);
        assertNotNull(executor);

        assertEquals(false, executor.isShutdown());
        context.stop();
        assertEquals(true, executor.isShutdown());
    }

    public void testThreadPoolBuilderDefault() throws Exception {
        ThreadPoolProfile profile = new ThreadPoolBuilder("myPool").build();
        getAndShutdown(profile);
    }

    public void testThreadPoolBuilderMaxQueueSize() throws Exception {
        ThreadPoolProfile profile = new ThreadPoolBuilder("myPool").maxQueueSize(2000).build();
        getAndShutdown(profile);
    }

    public void testThreadPoolBuilderMax() throws Exception {
        ThreadPoolProfile profile = new ThreadPoolBuilder("myPool").maxPoolSize(100).build();
        getAndShutdown(profile);
    }

    public void testThreadPoolBuilderCoreAndMax() throws Exception {
        ThreadPoolProfile profile = new ThreadPoolBuilder("myPool").poolSize(50).maxPoolSize(100).build();
        getAndShutdown(profile);
    }

    public void testThreadPoolBuilderKeepAlive() throws Exception {
        ThreadPoolProfile profile = new ThreadPoolBuilder("myPool").keepAliveTime(30).build();
        getAndShutdown(profile);
    }

    public void testThreadPoolBuilderKeepAliveTimeUnit() throws Exception {
        ThreadPoolProfile profile = new ThreadPoolBuilder("myPool").keepAliveTime(20000).timeUnit(TimeUnit.MILLISECONDS).build();
        getAndShutdown(profile);
    }

    public void testThreadPoolBuilderAll() throws Exception {
        ThreadPoolProfile profile = new ThreadPoolBuilder("myPool").poolSize(50).maxPoolSize(100).maxQueueSize(2000)
            .keepAliveTime(20000)
            .timeUnit(TimeUnit.MILLISECONDS)
            .rejectedPolicy(ThreadPoolRejectedPolicy.DiscardOldest)
            .build();
        getAndShutdown(profile);
    }

    public void testThreadPoolBuilderTwoPoolsDefault() throws Exception {
        ExecutorService executor = context.getExecutorServiceManager().getExecutorService(new ThreadPoolBuilder("myPool").build(), this);
        ExecutorService executor2 = context.getExecutorServiceManager().getExecutorService(new ThreadPoolBuilder("myOtherPool").build(), this);

        assertNotNull(executor);
        assertNotNull(executor2);

        assertEquals(false, executor.isShutdown());
        assertEquals(false, executor2.isShutdown());
        context.stop();
        assertEquals(true, executor.isShutdown());
        assertEquals(true, executor2.isShutdown());
    }


}
