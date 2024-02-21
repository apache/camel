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

import java.util.Set;

import io.vertx.core.Vertx;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
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
public class VertXReactiveExecutor extends ServiceSupport implements CamelContextAware, ReactiveExecutor, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(VertXReactiveExecutor.class);

    private CamelContext camelContext;
    private Vertx vertx;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

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
    protected void doInit() throws Exception {
        super.doInit();
        if (vertx == null) {
            Set<Vertx> set = getCamelContext().getRegistry().findByType(Vertx.class);
            if (set.size() == 1) {
                vertx = set.iterator().next();
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (vertx == null) {
            throw new IllegalArgumentException("VertX instance must be configured.");
        }
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
        vertx.executeBlocking(() -> {
            task.run();
            return null;
        });
    }

    @Override
    public void scheduleQueue(Runnable runnable) {
        // not supported so schedule sync
        scheduleSync(runnable);
    }

    @Override
    public boolean executeFromQueue() {
        // not supported so return false
        return false;
    }

    @Override
    public void setStatisticsEnabled(boolean statisticsEnabled) {
        // not in use
    }

    @Override
    public boolean isStatisticsEnabled() {
        // not in use
        return false;
    }

    @Override
    public String toString() {
        return "camel-reactive-executor-vertx";
    }
}
