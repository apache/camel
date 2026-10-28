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
package org.apache.camel.test.dst;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;

/**
 * A {@link ThreadPoolFactory} whose thread pools are executors of one {@link Dst} run: every task that Camel (or a
 * component) submits to a pool created through the {@code ExecutorServiceManager} runs on the deterministic loop.
 */
public class DstThreadPoolFactory implements ThreadPoolFactory {

    private final Dst dst;

    public DstThreadPoolFactory(Dst dst) {
        this.dst = dst;
    }

    @Override
    public ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return created(new DstExecutor(dst, label(threadFactory), Integer.MAX_VALUE));
    }

    @Override
    public ExecutorService newThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
        int threads = profile.getMaxPoolSize() != null && profile.getMaxPoolSize() > 0
                ? profile.getMaxPoolSize()
                : profile.getPoolSize() != null ? profile.getPoolSize() : 1;
        return created(new DstExecutor(dst, label(threadFactory), threads));
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
        int threads = profile.getPoolSize() != null && profile.getPoolSize() > 0 ? profile.getPoolSize() : 1;
        return created(new DstExecutor(dst, label(threadFactory), threads));
    }

    private DstExecutor created(DstExecutor executor) {
        dst.trace("thread pool created: " + executor.label() + " (" + executor.threads() + " threads)");
        return executor;
    }

    /**
     * The name Camel gives the threads of the pool, without the context name, the global thread counter and the
     * endpoint parameters (which differ between runs or may contain secrets).
     */
    static String label(ThreadFactory threadFactory) {
        // the thread is never started
        String name = threadFactory.newThread(() -> {
        }).getName();
        int dash = name.indexOf(" - ");
        if (name.startsWith("Camel") && dash > 0) {
            name = name.substring(dash + 3);
        }
        int query = name.indexOf('?');
        if (query > 0) {
            name = name.substring(0, query);
        }
        return name;
    }
}
