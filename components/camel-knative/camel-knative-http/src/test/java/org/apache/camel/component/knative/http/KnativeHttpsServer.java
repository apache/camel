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
package org.apache.camel.component.knative.http;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.CamelContext;
import org.apache.camel.test.AvailablePortFinder;

public class KnativeHttpsServer extends KnativeHttpServer {

    public KnativeHttpsServer(CamelContext context) {
        super(context, "localhost", AvailablePortFinder.getNextAvailable(), "/", null);
    }

    public KnativeHttpsServer(CamelContext context, int port) {
        super(context, "localhost", port, "/", null);
    }

    public KnativeHttpsServer(CamelContext context, int port, Handler<RoutingContext> handler) {
        super(context, "localhost", port, "/", handler);
    }

    public KnativeHttpsServer(CamelContext context, Handler<RoutingContext> handler) {
        super(context, "localhost", AvailablePortFinder.getNextAvailable(), "/", handler);
    }

    public KnativeHttpsServer(CamelContext context, String host, int port, String path) {
        super(context, host, port, path, null);
    }

    public KnativeHttpsServer(CamelContext context, String host, String path) {
        super(context, host, AvailablePortFinder.getNextAvailable(), path, null);
    }

    public KnativeHttpsServer(CamelContext context, String host, String path, Handler<RoutingContext> handler) {
        super(context, host, AvailablePortFinder.getNextAvailable(), path, handler);
    }

    @Override
    protected HttpServerOptions getServerOptions() {
        return new HttpServerOptions()
                .setSsl(true)
                .setKeyCertOptions(
                        new PemKeyCertOptions()
                                .setKeyPath("keystore/server.pem")
                                .setCertPath("keystore/server.crt"))
                .setTrustOptions(
                        new JksOptions()
                                .setPath("keystore/truststore.jks")
                                .setPassword("secr3t")

                );
    }
}
