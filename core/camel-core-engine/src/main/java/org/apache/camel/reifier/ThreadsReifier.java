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
package org.apache.camel.reifier;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ThreadsDefinition;
import org.apache.camel.processor.ThreadsProcessor;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;

public class ThreadsReifier extends ProcessorReifier<ThreadsDefinition> {

    public ThreadsReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (ThreadsDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        // the threads name
        String name = definition.getThreadName() != null ? parseString(definition.getThreadName()) : "Threads";
        // prefer any explicit configured executor service
        boolean shutdownThreadPool = willCreateNewThreadPool(definition, true);
        ExecutorService threadPool = getConfiguredExecutorService(name, definition, false);

        // resolve what rejected policy to use
        ThreadPoolRejectedPolicy policy = resolveRejectedPolicy();
        if (policy == null) {
            if (parseBoolean(definition.getCallerRunsWhenRejected(), true)) {
                // should use caller runs by default if not configured
                policy = ThreadPoolRejectedPolicy.CallerRuns;
            } else {
                policy = ThreadPoolRejectedPolicy.Abort;
            }
        }
        log.debug("Using ThreadPoolRejectedPolicy: {}", policy);

        // if no explicit then create from the options
        if (threadPool == null) {
            ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
            // create the thread pool using a builder
            ThreadPoolProfile profile = new ThreadPoolProfileBuilder(name)
                .poolSize(definition.getPoolSize() != null ? parseInt(definition.getPoolSize()) : null)
                .maxPoolSize(definition.getMaxPoolSize() != null ? parseInt(definition.getMaxPoolSize()) : null)
                .keepAliveTime(definition.getKeepAliveTime() != null ? parseLong(definition.getKeepAliveTime()) : null,
                               definition.getTimeUnit() != null ? parse(TimeUnit.class, definition.getTimeUnit()) : null)
                .maxQueueSize(definition.getMaxQueueSize() != null ? parseInt(definition.getMaxQueueSize()) : null)
                .rejectedPolicy(policy)
                .allowCoreThreadTimeOut(definition.getAllowCoreThreadTimeOut() != null ? parseBoolean(definition.getAllowCoreThreadTimeOut(), false) : null)
                .build();
            threadPool = manager.newThreadPool(definition, name, profile);
            shutdownThreadPool = true;
        } else {
            if (definition.getThreadName() != null && !definition.getThreadName().equals("Threads")) {
                throw new IllegalArgumentException("ThreadName and executorServiceRef options cannot be used together.");
            }
            if (definition.getPoolSize() != null) {
                throw new IllegalArgumentException("PoolSize and executorServiceRef options cannot be used together.");
            }
            if (definition.getMaxPoolSize() != null) {
                throw new IllegalArgumentException("MaxPoolSize and executorServiceRef options cannot be used together.");
            }
            if (definition.getKeepAliveTime() != null) {
                throw new IllegalArgumentException("KeepAliveTime and executorServiceRef options cannot be used together.");
            }
            if (definition.getTimeUnit() != null) {
                throw new IllegalArgumentException("TimeUnit and executorServiceRef options cannot be used together.");
            }
            if (definition.getMaxQueueSize() != null) {
                throw new IllegalArgumentException("MaxQueueSize and executorServiceRef options cannot be used together.");
            }
            if (definition.getRejectedPolicy() != null) {
                throw new IllegalArgumentException("RejectedPolicy and executorServiceRef options cannot be used together.");
            }
            if (definition.getAllowCoreThreadTimeOut() != null) {
                throw new IllegalArgumentException("AllowCoreThreadTimeOut and executorServiceRef options cannot be used together.");
            }
        }

        return new ThreadsProcessor(camelContext, threadPool, shutdownThreadPool, policy);
    }

    protected ThreadPoolRejectedPolicy resolveRejectedPolicy() {
        if (definition.getExecutorServiceRef() != null && definition.getRejectedPolicy() == null) {
            ThreadPoolProfile threadPoolProfile = camelContext.getExecutorServiceManager().getThreadPoolProfile(parseString(definition.getExecutorServiceRef()));
            if (threadPoolProfile != null) {
                return threadPoolProfile.getRejectedPolicy();
            }
        }
        return parse(ThreadPoolRejectedPolicy.class, definition.getRejectedPolicy());
    }

}
