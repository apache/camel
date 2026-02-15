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
package org.apache.camel.component.vertx.http;

import java.util.concurrent.TimeUnit;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.btc.BlockedThreadEvent;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;

public class VertxHttpTestSupport extends CamelTestSupport {

    protected final int port = AvailablePortFinder.getNextAvailable();

    protected String getTestServerUrl() {
        return String.format("http://localhost:%d", port);
    }

    protected String getTestServerUri() {
        return String.format("undertow:%s", getTestServerUrl());
    }

    protected String getProducerUri() {
        return String.format("vertx-http:http://localhost:%d", port);
    }

    protected int getPort() {
        return port;
    }

    protected Vertx createVertxWithThreadBlockedHandler(Handler<BlockedThreadEvent> handler) {
        VertxOptions vertxOptions = new VertxOptions();
        vertxOptions.setMaxEventLoopExecuteTime(500);
        vertxOptions.setMaxEventLoopExecuteTimeUnit(TimeUnit.MILLISECONDS);
        vertxOptions.setBlockedThreadCheckInterval(10);
        vertxOptions.setBlockedThreadCheckIntervalUnit(TimeUnit.MILLISECONDS);
        Vertx vertx = Vertx.vertx(vertxOptions);
        ((VertxInternal) vertx).blockedThreadChecker().setThreadBlockedHandler(handler);
        return vertx;
    }

    static final class BlockedThreadReporter implements Handler<BlockedThreadEvent> {
        private volatile boolean eventLoopBlocked;

        @Override
        public void handle(BlockedThreadEvent event) {
            VertxException stackTrace = new VertxException("Thread blocked");
            stackTrace.setStackTrace(event.thread().getStackTrace());
            eventLoopBlocked = true;
        }

        public boolean isEventLoopBlocked() {
            return eventLoopBlocked;
        }

        public void reset() {
            eventLoopBlocked = false;
        }
    }
}
