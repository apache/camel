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

import io.vertx.core.Vertx;
import org.apache.camel.Experimental;
import org.apache.camel.StaticService;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A VertX based {@link ReactiveExecutor} that uses Vert X event loop.
 * <p/>
 * NOTE: This is an experimental implementation (use with care)
 */
@Experimental
@JdkService(ReactiveExecutor.FACTORY)
public class VertXReactiveExecutor extends ServiceSupport implements ReactiveExecutor, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(VertXReactiveExecutor.class);

    private Vertx vertx;
    private boolean shouldClose;

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
    public void schedule(Runnable runnable) {
        LOG.trace("schedule: {}", runnable);
        vertx.nettyEventLoopGroup().execute(runnable);
    }

    @Override
    public void scheduleMain(Runnable runnable) {
        LOG.trace("scheduleMain: {}", runnable);
        vertx.nettyEventLoopGroup().execute(runnable);
    }

    @Override
    public void scheduleSync(Runnable runnable) {
        LOG.trace("scheduleSync: {}", runnable);
        final Runnable task = runnable;
        vertx.executeBlocking(future -> {
            task.run();
            future.complete();
        }, res -> { });
    }

    @Override
    public boolean executeFromQueue() {
        // not supported so return false
        return false;
    }

    @Override
    protected void doStart() throws Exception {
        if (vertx == null) {
            LOG.debug("Starting VertX");
            shouldClose = true;
            vertx = Vertx.vertx();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (vertx != null && shouldClose) {
            LOG.debug("Stopping VertX");
            vertx.close();
        }
    }

    @Override
    public String toString() {
        return "camel-reactive-executor-vertx";
    }
}
