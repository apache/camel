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
package org.apache.camel.component.vertx.websocket;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import org.apache.camel.support.jsse.SSLContextParameters;

public class VertxWebsocketHostConfiguration {
    private final Vertx vertx;
    private final Router router;
    private final HttpServerOptions serverOptions;
    private final SSLContextParameters sslContextParameters;

    public VertxWebsocketHostConfiguration(Vertx vertx, Router router, HttpServerOptions serverOptions,
                                           SSLContextParameters sslContextParameters) {
        this.vertx = vertx;
        this.router = router;
        this.serverOptions = serverOptions;
        this.sslContextParameters = sslContextParameters;
    }

    public Vertx getVertx() {
        return vertx;
    }

    public Router getRouter() {
        return router;
    }

    public HttpServerOptions getServerOptions() {
        return serverOptions;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }
}
