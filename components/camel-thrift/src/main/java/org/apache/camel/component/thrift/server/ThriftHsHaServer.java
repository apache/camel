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
package org.apache.camel.component.thrift.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.CamelContext;
import org.apache.thrift.server.TNonblockingServer;
import org.apache.thrift.transport.TNonblockingServerTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Thrift HsHaServer implementation with executors controlled by the Camel Executor Service Manager
 */
public class ThriftHsHaServer extends TNonblockingServer {
    private static final Logger LOG = LoggerFactory.getLogger(ThriftHsHaServer.class);

    public static class Args extends AbstractNonblockingServerArgs<Args> {
        private ExecutorService executorService;
        private ExecutorService startThreadPool;
        private CamelContext context;

        public Args(TNonblockingServerTransport transport) {
            super(transport);
        }

        public Args executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public Args startThreadPool(ExecutorService startThreadPool) {
            this.startThreadPool = startThreadPool;
            return this;
        }

        public Args context(CamelContext context) {
            this.context = context;
            return this;
        }
    }

    private final ExecutorService invoker;
    private final CamelContext context;
    private final ExecutorService startExecutor;

    public ThriftHsHaServer(Args args) {
        super(args);

        this.context = args.context;
        this.invoker = args.executorService;
        this.startExecutor = args.startThreadPool;
    }

    @Override
    public void serve() throws IllegalArgumentException {
        if (!startThreads()) {
            throw new IllegalArgumentException("Failed to start selector thread!");
        }

        if (!startListening()) {
            throw new IllegalArgumentException("Failed to start listening on server socket!");
        }

        startExecutor.execute(() -> {
            setServing(true);

            waitForShutdown();

            setServing(false);
            stopListening();
        });
    }

    @Override
    public void stop() {
        super.stop();
        context.getExecutorServiceManager().shutdownGraceful(startExecutor);
    }

    @Override
    protected void waitForShutdown() {
        joinSelector();
        context.getExecutorServiceManager().shutdownGraceful(invoker);
    }

    @Override
    protected boolean requestInvoke(FrameBuffer frameBuffer) {
        try {
            Runnable invocation = getRunnable(frameBuffer);
            invoker.execute(invocation);
            return true;
        } catch (RejectedExecutionException rx) {
            LOG.warn("ExecutorService rejected execution!", rx);
            return false;
        }
    }

    protected Runnable getRunnable(FrameBuffer frameBuffer) {
        return new Invocation(frameBuffer);
    }
}
