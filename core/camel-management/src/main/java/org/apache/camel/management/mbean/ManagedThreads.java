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

package org.apache.camel.management.mbean;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedThreadsMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.ThreadsProcessor;

@ManagedResource(description = "Managed Threads")
public class ManagedThreads extends ManagedProcessor implements ManagedThreadsMBean {

    public ManagedThreads(CamelContext context, ThreadsProcessor processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
    }

    @Override
    public ThreadsProcessor getProcessor() {
        return (ThreadsProcessor) super.getProcessor();
    }

    @Override
    public Boolean isCallerRunsWhenRejected() {
        if (getProcessor().getExecutorService() instanceof ThreadPoolExecutor) {
            String name = getRejectedPolicy();
            return "CallerRuns".equals(name);
        } else {
            return null;
        }
    }

    @Override
    public String getRejectedPolicy() {
        if (getProcessor().getExecutorService() instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.getRejectedExecutionHandler().toString();
        } else {
            return null;
        }
    }

    @Override
    public int getCorePoolSize() {
        if (getProcessor().getExecutorService() instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.getCorePoolSize();
        } else {
            return 0;
        }
    }

    @Override
    public int getPoolSize() {
        if (getProcessor().getExecutorService() instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.getPoolSize();
        } else {
            return 0;
        }
    }

    @Override
    public int getMaximumPoolSize() {
        if (getProcessor().getExecutorService() instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.getMaximumPoolSize();
        } else {
            return 0;
        }
    }

    @Override
    public int getLargestPoolSize() {
        if (getProcessor().getExecutorService() instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.getLargestPoolSize();
        } else {
            return 0;
        }
    }

    @Override
    public int getActiveCount() {
        if (getProcessor().getExecutorService() instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.getActiveCount();
        } else {
            return 0;
        }
    }

    @Override
    public long getTaskCount() {
        if (getProcessor().getExecutorService() instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.getTaskCount();
        } else {
            return 0;
        }
    }

    @Override
    public long getCompletedTaskCount() {
        if (getProcessor().getExecutorService() instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.getCompletedTaskCount();
        } else {
            return 0;
        }
    }

    @Override
    public long getTaskQueueSize() {
        if (getProcessor().getExecutorService() instanceof ThreadPoolExecutor threadPoolExecutor) {
            BlockingQueue<Runnable> queue = threadPoolExecutor.getQueue();
            return queue != null ? queue.size() : 0;
        } else {
            return 0;
        }
    }

    @Override
    public long getKeepAliveTime() {
        if (getProcessor().getExecutorService() instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.getKeepAliveTime(TimeUnit.SECONDS);
        } else {
            return 0;
        }
    }

    @Override
    public boolean isAllowCoreThreadTimeout() {
        if (getProcessor().getExecutorService() instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.allowsCoreThreadTimeOut();
        } else {
            return false;
        }
    }
}
