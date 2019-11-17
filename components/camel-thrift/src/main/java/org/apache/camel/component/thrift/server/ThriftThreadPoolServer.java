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
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerTransport;

/*
 * Thrift ThreadPoolServer implementation with executors controlled by the Camel Executor Service Manager
 */
public class ThriftThreadPoolServer extends TThreadPoolServer {

    public static class Args extends TThreadPoolServer.Args {
        private ExecutorService startThreadPool;
        private CamelContext context;
        
        public Args(TServerTransport transport) {
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

    // Executor service for handling client connections
    private final CamelContext context;
    private final ExecutorService startExecutor;


    public ThriftThreadPoolServer(Args args) {
        super(args);

        context = args.context;
        startExecutor = args.startThreadPool;
    }

    @Override
    public void serve() {
        if (!preServe()) {
            return;
        }

        startExecutor.execute(() -> {
            execute();
            waitForShutdown();
            
            context.getExecutorServiceManager().shutdownGraceful(getExecutorService());
            setServing(false);
        });
    }

    @Override
    public void stop() {
        super.stop();
        context.getExecutorServiceManager().shutdownGraceful(startExecutor);
    }
}
