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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.Message;
import org.apache.camel.converter.stream.CachedOutputStream;

import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpSupport.isFormUrlEncoded;
import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpSupport.isMultiPartFormData;

/**
 * A {@link HttpRequestBodyHandler} that can handle large request bodies via {@link CachedOutputStream}.
 */
class StreamingHttpRequestBodyHandler extends HttpRequestBodyHandler {
    StreamingHttpRequestBodyHandler(Handler<RoutingContext> delegate) {
        super(delegate);
    }

    @Override
    void configureRoute(Route route) {
        // No configuration necessary for streaming
    }

    @Override
    Future<Void> handle(RoutingContext routingContext, Message message) {
        // Reject multipart requests if streaming enabled as we can't be sure when Vert.x has
        // fully written the attachments to disk after invoking the default body handler.
        if (isMultiPartFormData(routingContext)) {
            return Future.failedFuture(
                    new IllegalStateException("Cannot process multipart/form-data requests when useStreaming=true"));
        }

        Promise<Void> promise = Promise.promise();
        HttpServerRequest request = routingContext.request();
        if (isFormUrlEncoded(routingContext)) {
            // Delegate body handling to the default body handler
            delegate.handle(routingContext);
            request.endHandler(promise::complete);
        } else {
            // Process each body 'chunk' and write it to CachedOutputStream
            CachedOutputStream stream = new CachedOutputStream(message.getExchange(), true);
            AtomicReference<Exception> failureCause = new AtomicReference<>();
            request.handler(buffer -> {
                try {
                    stream.write(buffer.getBytes());
                } catch (IOException e) {
                    failureCause.set(e);
                }
            });
            // After the body is read, close the CachedOutputStream and get an InputStream to use as the message body
            request.endHandler(event -> {
                try {
                    stream.close();

                    Exception failure = failureCause.get();
                    if (failure == null) {
                        message.setBody(stream.getInputStream());
                        promise.complete();
                    } else {
                        promise.fail(failure);
                    }
                } catch (IOException e) {
                    promise.fail(e);
                }
            });
        }

        return promise.future();
    }
}
