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
        String name = parseString(definition.getThreadName());
        if (name == null || name.isEmpty()) {
            name = "Threads";
        }
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

        // if no explicit then create from the options
        if (threadPool == null) {
            ThreadPoolProfile profile = new ThreadPoolProfile(name);
            profile.setPoolSize(definition.getPoolSize() != null ? parseInt(definition.getPoolSize()) : null);
            profile.setMaxPoolSize(definition.getMaxPoolSize() != null ? parseInt(definition.getMaxPoolSize()) : null);
            profile.setKeepAliveTime(
                    definition.getKeepAliveTime() != null ? parseDuration(definition.getKeepAliveTime()) : null);
            profile.setTimeUnit(definition.getTimeUnit() != null ? parse(TimeUnit.class, definition.getTimeUnit()) : null);
            profile.setMaxQueueSize(definition.getMaxQueueSize() != null ? parseInt(definition.getMaxQueueSize()) : null);
            profile.setRejectedPolicy(policy);
            profile.setAllowCoreThreadTimeOut(definition.getAllowCoreThreadTimeOut() != null
                    ? parseBoolean(definition.getAllowCoreThreadTimeOut(), false) : null);

            ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
            threadPool = manager.newThreadPool(definition, name, profile);
            shutdownThreadPool = true;
        } else {
            if (definition.getThreadName() != null && !definition.getThreadName().equals("Threads")) {
                throw new IllegalArgumentException("ThreadName and executorService options cannot be used together.");
            }
            if (definition.getPoolSize() != null) {
                throw new IllegalArgumentException("PoolSize and executorService options cannot be used together.");
            }
            if (definition.getMaxPoolSize() != null) {
                throw new IllegalArgumentException("MaxPoolSize and executorService options cannot be used together.");
            }
            if (definition.getKeepAliveTime() != null) {
                throw new IllegalArgumentException("KeepAliveTime and executorService options cannot be used together.");
            }
            if (definition.getTimeUnit() != null) {
                throw new IllegalArgumentException("TimeUnit and executorService options cannot be used together.");
            }
            if (definition.getMaxQueueSize() != null) {
                throw new IllegalArgumentException("MaxQueueSize and executorService options cannot be used together.");
            }
            if (definition.getRejectedPolicy() != null) {
                throw new IllegalArgumentException("RejectedPolicy and executorService options cannot be used together.");
            }
            if (definition.getAllowCoreThreadTimeOut() != null) {
                throw new IllegalArgumentException(
                        "AllowCoreThreadTimeOut and executorService options cannot be used together.");
            }
        }

        return new ThreadsProcessor(camelContext, threadPool, shutdownThreadPool, policy);
    }

    protected ThreadPoolRejectedPolicy resolveRejectedPolicy() {
        String ref = parseString(definition.getExecutorService());
        if (ref != null && definition.getRejectedPolicy() == null) {
            ThreadPoolProfile threadPoolProfile = camelContext.getExecutorServiceManager()
                    .getThreadPoolProfile(ref);
            if (threadPoolProfile != null) {
                return threadPoolProfile.getRejectedPolicy();
            }
        }
        return parse(ThreadPoolRejectedPolicy.class, definition.getRejectedPolicy());
    }

}
