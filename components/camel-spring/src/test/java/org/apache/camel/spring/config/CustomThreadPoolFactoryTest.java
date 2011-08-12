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
package org.apache.camel.spring.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultThreadPoolFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

/**
 * @version 
 */
@ContextConfiguration
public class CustomThreadPoolFactoryTest extends AbstractJUnit38SpringContextTests {

    @Autowired
    protected CamelContext context;

    public void testCustomThreadPoolFactory() throws Exception {
        context.getExecutorServiceManager().newSingleThreadExecutor(this, "foo");
        Assert.assertTrue(context.getExecutorServiceManager().getThreadPoolFactory() instanceof MyCustomThreadPoolFactory);

        MyCustomThreadPoolFactory factory = (MyCustomThreadPoolFactory) context.getExecutorServiceManager().getThreadPoolFactory();
        assertTrue("Should use custom thread pool factory", factory.isInvoked());
    }

    public static class MyCustomThreadPoolFactory extends DefaultThreadPoolFactory {

        private volatile boolean invoked;

        @Override
        public ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
            invoked = true;
            return super.newCachedThreadPool(threadFactory);
        }

        @Override
        public ExecutorService newFixedThreadPool(int poolSize, ThreadFactory threadFactory) {
            invoked = true;
            return super.newFixedThreadPool(poolSize, threadFactory);
        }

        @Override
        public ScheduledExecutorService newScheduledThreadPool(int corePoolSize, ThreadFactory threadFactory) throws IllegalArgumentException {
            invoked = true;
            return super.newScheduledThreadPool(corePoolSize, threadFactory);
        }

        @Override
        public ExecutorService newThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit, int maxQueueSize, RejectedExecutionHandler rejectedExecutionHandler, ThreadFactory threadFactory) throws IllegalArgumentException {
            invoked = true;
            return super.newThreadPool(corePoolSize, maxPoolSize, keepAliveTime, timeUnit, maxQueueSize, rejectedExecutionHandler, threadFactory);
        }

        public boolean isInvoked() {
            return invoked;
        }
    }


}