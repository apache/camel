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
package org.apache.camel.reactive.vertx;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.Vertx;
import org.apache.camel.spi.SimpleExecutorService;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.DefaultThreadPoolFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService(ThreadPoolFactory.FACTORY)
public class VertXThreadPoolFactory extends DefaultThreadPoolFactory implements ThreadPoolFactory {

    private static final Logger LOG = LoggerFactory.getLogger(VertXThreadPoolFactory.class);

    private final ExecutorService vertxExecutorService = new VertXExecutorService();
    private Vertx vertx;

    public Vertx getVertx() {
        return vertx;
    }

    /**
     * To use an existing instance of {@link Vertx} instead of creating a default instance.
     */
    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (vertx == null) {
            Set<Vertx> set = getCamelContext().getRegistry().findByType(Vertx.class);
            if (set.size() == 1) {
                vertx = set.iterator().next();
            }
        }
        if (vertx == null) {
            throw new IllegalArgumentException("VertX instance must be configured.");
        }
    }

    @Override
    public ExecutorService newThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
        // only do this for default profile (which mean its not a custom pool)
        if (profile.isDefaultProfile()) {
            return vertxExecutorService;
        } else {
            return super.newThreadPool(profile, threadFactory);
        }
    }

    @Override
    public ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return vertxExecutorService;
    }

    @Override
    public String toString() {
        return "camel-threadpoolfactory-vertx";
    }

    private class VertXExecutorService implements ExecutorService, SimpleExecutorService {

        @Override
        public void shutdown() {
            // noop
        }

        @Override
        public List<Runnable> shutdownNow() {
            // noop
            return null;
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return false;
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return null;
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return null;
        }

        @Override
        public Future<?> submit(Runnable task) {
            LOG.trace("submit: {}", task);
            final CompletableFuture<?> answer = new CompletableFuture<>();
            // used by vertx
            vertx.executeBlocking(() -> {
                task.run();
                return null;
            }).onComplete(res -> answer.complete(null));
            return answer;
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return null;
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws InterruptedException {
            return null;
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }

        @Override
        public void execute(Runnable command) {
            LOG.trace("execute: {}", command);
            // used by vertx
            vertx.executeBlocking(future -> {
                command.run();
                future.complete();
            }, null);
        }
    }

}
