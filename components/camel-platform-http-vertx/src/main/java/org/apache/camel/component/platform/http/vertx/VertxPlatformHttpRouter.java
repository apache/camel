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

import java.util.List;
import java.util.Map;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.AllowForwardHeaders;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.component.platform.http.PlatformHttpConstants;
import org.apache.camel.support.CamelContextHelper;

public class VertxPlatformHttpRouter implements Router {
    public static final String PLATFORM_HTTP_ROUTER_NAME = PlatformHttpConstants.PLATFORM_HTTP_COMPONENT_NAME + "-router";

    private final VertxPlatformHttpServer server;
    private final Vertx vertx;
    private final Router delegate;
    private AllowForwardHeaders allowForward;

    public VertxPlatformHttpRouter(VertxPlatformHttpServer server, Vertx vertx, Router delegate) {
        this.server = server;
        this.vertx = vertx;
        this.delegate = delegate;
        this.allowForward = AllowForwardHeaders.NONE;
    }

    public Vertx vertx() {
        return vertx;
    }

    public VertxPlatformHttpServer getServer() {
        return server;
    }

    @Override
    public Route route() {
        return delegate.route();
    }

    @Override
    public Route route(HttpMethod method, String s) {
        return delegate.route(method, s);
    }

    @Override
    public Route route(String s) {
        return delegate.route(s);
    }

    @Override
    public Route routeWithRegex(HttpMethod method, String s) {
        return delegate.routeWithRegex(method, s);
    }

    @Override
    public Route routeWithRegex(String s) {
        return delegate.routeWithRegex(s);
    }

    @Override
    public Route get() {
        return delegate.get();
    }

    @Override
    public Route get(String s) {
        return delegate.get(s);
    }

    @Override
    public Route getWithRegex(String s) {
        return delegate.getWithRegex(s);
    }

    @Override
    public Route head() {
        return delegate.head();
    }

    @Override
    public Route head(String s) {
        return delegate.head(s);
    }

    @Override
    public Route headWithRegex(String s) {
        return delegate.headWithRegex(s);
    }

    @Override
    public Route options() {
        return delegate.options();
    }

    @Override
    public Route options(String s) {
        return delegate.options(s);
    }

    @Override
    public Route optionsWithRegex(String s) {
        return delegate.optionsWithRegex(s);
    }

    @Override
    public Route put() {
        return delegate.put();
    }

    @Override
    public Route put(String s) {
        return delegate.put(s);
    }

    @Override
    public Route putWithRegex(String s) {
        return delegate.putWithRegex(s);
    }

    @Override
    public Route post() {
        return delegate.post();
    }

    @Override
    public Route post(String s) {
        return delegate.post(s);
    }

    @Override
    public Route postWithRegex(String s) {
        return delegate.postWithRegex(s);
    }

    @Override
    public Route delete() {
        return delegate.delete();
    }

    @Override
    public Route delete(String s) {
        return delegate.delete(s);
    }

    @Override
    public Route deleteWithRegex(String s) {
        return delegate.deleteWithRegex(s);
    }

    @Override
    public Route trace() {
        return delegate.trace();
    }

    @Override
    public Route trace(String s) {
        return delegate.trace(s);
    }

    @Override
    public Route traceWithRegex(String s) {
        return delegate.traceWithRegex(s);
    }

    @Override
    public Route connect() {
        return delegate.connect();
    }

    @Override
    public Route connect(String s) {
        return delegate.connect(s);
    }

    @Override
    public Route connectWithRegex(String s) {
        return delegate.connectWithRegex(s);
    }

    @Override
    public Route patch() {
        return delegate.patch();
    }

    @Override
    public Route patch(String s) {
        return delegate.patch(s);
    }

    @Override
    public Route patchWithRegex(String s) {
        return delegate.patchWithRegex(s);
    }

    @Override
    public List<Route> getRoutes() {
        return delegate.getRoutes();
    }

    @Override
    public Router clear() {
        return delegate.clear();
    }

    @Override
    public Route mountSubRouter(String mountPoint, Router subRouter) {
        if (mountPoint.endsWith("*")) {
            throw new IllegalArgumentException("Don't include * when mounting a sub router");
        }

        return route(mountPoint + "*")
                .subRouter(subRouter);
    }

    @Override
    public Router errorHandler(int i, Handler<RoutingContext> handler) {
        return delegate.errorHandler(i, handler);
    }

    @Override
    public void handleContext(RoutingContext context) {
        delegate.handleContext(context);
    }

    @Override
    public void handleFailure(RoutingContext context) {
        delegate.handleFailure(context);
    }

    @Override
    public Router modifiedHandler(Handler<Router> handler) {
        return delegate.modifiedHandler(handler);
    }

    @Override
    public Router allowForward(AllowForwardHeaders allowForwardHeaders) {
        this.allowForward = allowForwardHeaders;
        return this;
    }

    @Override
    public void handle(HttpServerRequest request) {
        delegate.handle(request);
    }

    @Override
    public Router putMetadata(String key, Object value) {
        return delegate.putMetadata(key, value);
    }

    @Override
    public Map<String, Object> metadata() {
        return delegate.metadata();
    }

    public Handler<RoutingContext> bodyHandler() {
        return BodyHandler.create();
    }

    // **********************
    //
    // Helpers
    //
    // **********************

    public static VertxPlatformHttpRouter lookup(CamelContext camelContext) {
        return CamelContextHelper.mandatoryLookup(
                camelContext,
                VertxPlatformHttpRouter.PLATFORM_HTTP_ROUTER_NAME,
                VertxPlatformHttpRouter.class);
    }
}
