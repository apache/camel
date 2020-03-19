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
package org.apache.camel.component.platform.http.vertx;

import java.util.Collections;
import java.util.List;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.CamelContext;
import org.apache.camel.component.platform.http.PlatformHttpConstants;
import org.apache.camel.support.CamelContextHelper;

/**
 * This class holds an instance of a {@link Router} and an optional list of [{@link Handler<RoutingContext>} that are
 * used by the {@link VertxPlatformHttpConsumer} to create and register Vert.x {@link io.vertx.ext.web.Route}].
 */
public class VertxPlatformHttp {
    public static final String PLATFORM_HTTP_ROUTER_NAME = PlatformHttpConstants.PLATFORM_HTTP_COMPONENT_NAME + "-router";

    private final Vertx vertx;
    private final Router router;
    private final List<Handler<RoutingContext>> handlers;

    public VertxPlatformHttp(Vertx vertx, Router router) {
        this(vertx, router, Collections.emptyList());
    }

    public VertxPlatformHttp(Vertx vertx, Router router, List<Handler<RoutingContext>> handlers) {
        this.vertx = vertx;
        this.router = router;
        this.handlers = handlers;
    }

    public Vertx vertx() {
        return this.vertx;
    }

    public Router router() {
        return router;
    }

    public List<Handler<RoutingContext>> handlers() {
        return handlers;
    }

    // **********************
    //
    // Helpers
    //
    // **********************

    public static VertxPlatformHttp lookup(CamelContext camelContext) {
        return CamelContextHelper.mandatoryLookup(
            camelContext,
            VertxPlatformHttp.PLATFORM_HTTP_ROUTER_NAME,
            VertxPlatformHttp.class
        );
    }
}
