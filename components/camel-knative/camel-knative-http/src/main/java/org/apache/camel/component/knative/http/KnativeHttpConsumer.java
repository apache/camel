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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.TypeConverter;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.knative.spi.KnativeResource;
import org.apache.camel.component.knative.spi.KnativeTransportConfiguration;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.CollectionHelper.appendEntry;

@ManagedResource(description = "Managed KnativeHttpConsumer")
public class KnativeHttpConsumer extends DefaultConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnativeHttpConsumer.class);

    private final KnativeTransportConfiguration configuration;
    private final Predicate<HttpServerRequest> filter;
    private final KnativeResource resource;
    private final Supplier<Router> router;
    private final HeaderFilterStrategy headerFilterStrategy;
    private volatile String path;

    private String basePath;
    private Route route;
    private BigInteger maxBodySize;
    private boolean preallocateBodyBuffer;

    public KnativeHttpConsumer(KnativeTransportConfiguration configuration,
                               Endpoint endpoint,
                               KnativeResource resource,
                               Supplier<Router> router,
                               Processor processor) {
        super(endpoint, processor);
        this.configuration = configuration;
        this.resource = resource;
        this.router = router;
        this.headerFilterStrategy = new KnativeHttpHeaderFilterStrategy();
        this.filter = KnativeHttpSupport.createFilter(this.configuration.getCloudEvent(), resource);
        this.preallocateBodyBuffer = true;
    }

    @ManagedAttribute(description = "Path for accessing the Knative service")
    public String getPath() {
        return path;
    }

    @ManagedAttribute(description = "Base path")
    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    @ManagedAttribute(description = "Maximum body size")
    public BigInteger getMaxBodySize() {
        return maxBodySize;
    }

    public void setMaxBodySize(BigInteger maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    @ManagedAttribute(description = "Preallocate body buffer")
    public boolean isPreallocateBodyBuffer() {
        return preallocateBodyBuffer;
    }

    public void setPreallocateBodyBuffer(boolean preallocateBodyBuffer) {
        this.preallocateBodyBuffer = preallocateBodyBuffer;
    }

    @Override
    protected void doStart() throws Exception {
        if (route == null) {
            path = resource.getPath();
            if (ObjectHelper.isEmpty(path)) {
                path = "/";
            }
            if (ObjectHelper.isNotEmpty(basePath)) {
                path = basePath + path;
            }

            LOGGER.debug("Creating route for path: {}", path);

            route = router.get().route(
                    HttpMethod.POST,
                    path);

            BodyHandler bodyHandler = BodyHandler.create();
            bodyHandler.setPreallocateBodyBuffer(this.preallocateBodyBuffer);
            if (this.maxBodySize != null) {
                bodyHandler.setBodyLimit(this.maxBodySize.longValueExact());
            }

            // add body handler
            route.handler((RoutingContext event) -> {
                event.request().resume();
                bodyHandler.handle(event);
            });

            // add knative handler
            route.handler(routingContext -> {
                LOGGER.debug("Handling {}", routingContext);

                if (filter.test(routingContext.request())) {
                    handleRequest(routingContext);
                } else {
                    LOGGER.debug("Cannot handle request on {}, next", getEndpoint().getEndpointUri());
                    routingContext.next();
                }
            });
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (route != null) {
            route.remove();
        }

        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        if (route != null) {
            route.disable();
        }
    }

    @Override
    protected void doResume() throws Exception {
        if (route != null) {
            route.enable();
        }
    }

    private void handleRequest(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();
        final Exchange exchange = getEndpoint().createExchange();
        final Message message = toMessage(request, exchange);

        Buffer payload = routingContext.body().buffer();
        if (payload != null) {
            message.setBody(payload.getBytes());
        } else {
            message.setBody(null);
        }

        // We do not know if any of the processing logic of the route is synchronous or not so we
        // need to process the request on a thread on the Vert.x worker pool.
        //
        // As example the following route may block the Vert.x event loop as the camel-http component
        // is not async so if the service is scaled-down, then it may take a while to become ready and
        // the camel-http component blocks until the service becomes available.
        //
        // from("knative:event/my.event")
        //        .to("http://{{env:PROJECT}}.{{env:NAMESPACE}}.svc.cluster.local/service");
        //
        routingContext.vertx().executeBlocking(() -> {
            createUoW(exchange);
            getAsyncProcessor().process(exchange);
            return null;
        },
                false)
                .onComplete(result -> {
                    try {
                        Throwable failure = null;

                        if (result.succeeded()) {
                            try {
                                HttpServerResponse response = toHttpResponse(request, exchange.getMessage());
                                Buffer body = null;

                                if (request.response().getStatusCode() != 204 && configuration.isReply()) {
                                    body = computeResponseBody(exchange.getMessage());

                                    String contentType = MessageHelper.getContentType(exchange.getMessage());
                                    if (contentType != null) {
                                        response.putHeader(Exchange.CONTENT_TYPE, contentType);
                                    }
                                }

                                if (body != null) {
                                    request.response().end(body);
                                } else {
                                    request.response().setStatusCode(204);
                                    request.response().end();
                                }
                            } catch (Exception e) {
                                failure = e;
                            }
                        } else if (result.failed()) {
                            failure = result.cause();
                        }

                        if (failure != null) {
                            getExceptionHandler().handleException(failure);
                            routingContext.fail(failure);
                        }
                    } finally {
                        doneUoW(exchange);
                    }
                });
    }

    private Message toMessage(HttpServerRequest request, Exchange exchange) {
        Message message = exchange.getMessage();
        String path = request.path();

        if (resource.getPath() != null) {
            String endpointPath = resource.getPath();
            String matchPath = path.toLowerCase(Locale.US);
            String match = endpointPath.toLowerCase(Locale.US);

            if (matchPath.startsWith(match)) {
                path = path.substring(endpointPath.length());
            }
        }

        for (Map.Entry<String, String> entry : request.headers().entries()) {
            if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), exchange)) {
                appendEntry(message.getHeaders(), entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, String> entry : request.params().entries()) {
            if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), exchange)) {
                appendEntry(message.getHeaders(), entry.getKey(), entry.getValue());
            }
        }

        message.setHeader(Exchange.HTTP_PATH, path);
        message.setHeader(Exchange.HTTP_METHOD, request.method());
        message.setHeader(Exchange.HTTP_URI, request.uri());
        message.setHeader(Exchange.HTTP_QUERY, request.query());

        return message;
    }

    private HttpServerResponse toHttpResponse(HttpServerRequest request, Message message) {
        final HttpServerResponse response = request.response();
        final boolean failed = message.getExchange().isFailed();
        final int defaultCode = failed ? 500 : 200;
        final int code = message.getHeader(Exchange.HTTP_RESPONSE_CODE, defaultCode, int.class);
        final TypeConverter tc = message.getExchange().getContext().getTypeConverter();

        response.setStatusCode(code);

        if (configuration.isReply()) {
            for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
                final String key = entry.getKey();
                final Object value = entry.getValue();

                for (Object it : org.apache.camel.support.ObjectHelper.createIterable(value, null)) {
                    String headerValue = tc.convertTo(String.class, it);
                    if (headerValue == null) {
                        continue;
                    }
                    if (!headerFilterStrategy.applyFilterToCamelHeaders(key, headerValue, message.getExchange())) {
                        response.putHeader(key, headerValue);
                    }
                }
            }

            KnativeHttpSupport.remapCloudEventHeaders(configuration.getCloudEvent(), message);
            if (configuration.isRemoveCloudEventHeadersInReply()) {
                KnativeHttpSupport.removeCloudEventHeaders(configuration.getCloudEvent(), message);
            }
        }

        return response;
    }

    private Buffer computeResponseBody(Message message) throws NoTypeConversionAvailableException {
        Object body = message.getBody();
        Exception exception = message.getExchange().getException();

        if (exception != null) {
            // we failed due an exception so print it as plain text
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);

            try {
                exception.printStackTrace(pw);

                // the body should then be the stacktrace
                body = sw.toString().getBytes(StandardCharsets.UTF_8);
                // force content type to be text/plain as that is what the stacktrace is
                message.setHeader(Exchange.CONTENT_TYPE, "text/plain");

                // and mark the exception as failure handled, as we handled it by returning
                // it as the response
                ExchangeHelper.setFailureHandled(message.getExchange());
            } finally {
                IOHelper.close(pw, sw);
            }
        }

        return body != null
                ? Buffer.buffer(message.getExchange().getContext().getTypeConverter().mandatoryConvertTo(byte[].class, body))
                : null;
    }
}
