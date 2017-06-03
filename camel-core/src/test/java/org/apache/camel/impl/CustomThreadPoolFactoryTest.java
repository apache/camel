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
package org.apache.camel.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;

/**
 *
 */
public class CustomThreadPoolFactoryTest extends ContextTestSupport {

    private MyCustomThreadPoolFactory factory = new MyCustomThreadPoolFactory();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getExecutorServiceManager().setThreadPoolFactory(factory);
        return context;
    }

    public void testCustomThreadPoolFactory() {
        context.getExecutorServiceManager().newSingleThreadExecutor(this, "foo");
        assertTrue("Should use custom thread pool factory", factory.isInvoked());
    }

    public static final class MyCustomThreadPoolFactory extends DefaultThreadPoolFactory {

        private volatile boolean invoked;

        @Override
        public ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
            invoked = true;
            return super.newCachedThreadPool(threadFactory);
        }

        @Override
        public ExecutorService newThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit, int maxQueueSize,
                                             boolean allowCoreThreadTimeOut, RejectedExecutionHandler rejectedExecutionHandler, ThreadFactory threadFactory) throws IllegalArgumentException {
            invoked = true;
            return super.newThreadPool(corePoolSize, maxPoolSize, keepAliveTime, timeUnit, maxQueueSize, allowCoreThreadTimeOut, rejectedExecutionHandler, threadFactory);
        }

        public boolean isInvoked() {
            return invoked;
        }
    }
}
