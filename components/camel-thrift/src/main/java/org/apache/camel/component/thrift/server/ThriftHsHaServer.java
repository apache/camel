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
package org.apache.camel.component.thrift.server;

import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.transport.TNonblockingServerTransport;

/*
 * Thrift HsHaServer implementation with executors controlled by the Camel Executor Service Manager
 */
public class ThriftHsHaServer extends THsHaServer {

    public static class Args extends THsHaServer.Args {
        private ExecutorService startThreadPool;
        private CamelContext context;

        public Args(TNonblockingServerTransport transport) {
            super(transport);
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

    private final CamelContext context;
    private final ExecutorService startExecutor;

    public ThriftHsHaServer(Args args) {
        super(args);

        this.context = args.context;
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
        context.getExecutorServiceManager().shutdownGraceful(getInvoker());
    }
}
