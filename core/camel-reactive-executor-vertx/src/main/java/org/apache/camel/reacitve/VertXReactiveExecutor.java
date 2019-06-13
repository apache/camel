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
package org.apache.camel.reacitve;

import io.vertx.core.Vertx;
import org.apache.camel.StaticService;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A VertX based {@link ReactiveExecutor} that uses Vert X event loop.
 */
public class VertXReactiveExecutor extends ServiceSupport implements ReactiveExecutor, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(VertXReactiveExecutor.class);

    private Vertx vertx;

    @Override
    public void schedule(Runnable runnable, String description) {
        LOG.trace("schedule: {}", runnable);
        vertx.nettyEventLoopGroup().execute(runnable);
    }

    @Override
    public void scheduleMain(Runnable runnable, String description) {
        LOG.trace("scheduleMain: {}", runnable);
        vertx.nettyEventLoopGroup().execute(runnable);
    }

    @Override
    public void scheduleSync(Runnable runnable, String description) {
        LOG.trace("scheduleSync: {}", runnable);
        vertx.executeBlocking(future -> {
            runnable.run();
            future.complete();
        }, res -> {});
    }

    @Override
    public boolean executeFromQueue() {
        LOG.trace("executeFromQueue");
        // TODO: not implemented
        return false;
    }

    @Override
    protected void doStart() throws Exception {
        LOG.debug("Starting VertX");
        vertx = Vertx.vertx();
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Stopping VertX");
        vertx.close();
    }
}
