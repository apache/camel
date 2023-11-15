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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.Message;

import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpSupport.isFormUrlEncoded;
import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpSupport.isMultiPartFormData;

/**
 * Default {@link HttpRequestBodyHandler} that will read to read the entire HTTP request body into memory.
 */
class DefaultHttpRequestBodyHandler extends HttpRequestBodyHandler {
    DefaultHttpRequestBodyHandler(Handler<RoutingContext> delegate) {
        super(delegate);
    }

    @Override
    void configureRoute(Route route) {
        route.handler(delegate);
    }

    @Override
    Future<Void> handle(RoutingContext routingContext, Message message) {
        if (!isMultiPartFormData(routingContext) && !isFormUrlEncoded(routingContext)) {
            final RequestBody requestBody = routingContext.body();
            message.setBody(requestBody.buffer());
        }
        return Future.succeededFuture();
    }
}
