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

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.activation.DataHandler;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.RouteImpl;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.SuspendableService;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.CamelFileDataSource;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.component.platform.http.spi.Method;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpSupport.appendHeader;
import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpSupport.populateCamelHeaders;
import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpSupport.writeResponse;

/**
 * A {@link org.apache.camel.Consumer} for the {@link org.apache.camel.component.platform.http.spi.PlatformHttpEngine}
 * based on Vert.x Web.
 */
public class VertxPlatformHttpConsumer extends DefaultConsumer implements Suspendable, SuspendableService {
    private static final Logger LOGGER = LoggerFactory.getLogger(VertxPlatformHttpConsumer.class);
    private static final Pattern PATH_PARAMETER_PATTERN = Pattern.compile("\\{([^/}]+)\\}");

    private final List<Handler<RoutingContext>> handlers;
    private final String fileNameExtWhitelist;
    private final boolean muteExceptions;
    private Set<Method> methods;
    private String path;
    private Route route;
    private VertxPlatformHttpRouter router;

    public VertxPlatformHttpConsumer(PlatformHttpEndpoint endpoint,
                                     Processor processor,
                                     List<Handler<RoutingContext>> handlers) {
        super(endpoint, processor);

        this.handlers = handlers;
        this.fileNameExtWhitelist
                = endpoint.getFileNameExtWhitelist() == null ? null : endpoint.getFileNameExtWhitelist().toLowerCase(Locale.US);
        this.muteExceptions = endpoint.isMuteException();
    }

    @Override
    public PlatformHttpEndpoint getEndpoint() {
        return (PlatformHttpEndpoint) super.getEndpoint();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        methods = Method.parseList(getEndpoint().getHttpMethodRestrict());
        path = configureEndpointPath(getEndpoint());
        router = VertxPlatformHttpRouter.lookup(getEndpoint().getCamelContext());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final Route newRoute = router.route(path);

        if (getEndpoint().getCamelContext().getRestConfiguration().isEnableCORS() && getEndpoint().getConsumes() != null) {
            ((RouteImpl) newRoute).setEmptyBodyPermittedWithConsumes(true);
        }

        if (!methods.equals(Method.getAll())) {
            methods.forEach(m -> newRoute.method(HttpMethod.valueOf(m.name())));
        }

        if (getEndpoint().getConsumes() != null) {
            //comma separated contentTypes has to be registered one by one
            for (String c : getEndpoint().getConsumes().split(",")) {
                newRoute.consumes(c);
            }
        }
        if (getEndpoint().getProduces() != null) {
            //comma separated contentTypes has to be registered one by one
            for (String p : getEndpoint().getProduces().split(",")) {
                newRoute.produces(p);
            }
        }

        newRoute.handler(router.bodyHandler());
        for (Handler<RoutingContext> handler : handlers) {
            newRoute.handler(handler);
        }

        newRoute.handler(this::handleRequest);

        this.route = newRoute;
    }

    @Override
    protected void doStop() throws Exception {
        if (route != null) {
            route.remove();
            route = null;
        }
        super.doStop();
    }

    private String configureEndpointPath(PlatformHttpEndpoint endpoint) {
        String path = endpoint.getPath();
        if (endpoint.isMatchOnUriPrefix() && !path.endsWith("*")) {
            path += "*";
        }
        // Transform from the Camel path param syntax /path/{key} to vert.x web's /path/:key
        return PATH_PARAMETER_PATTERN.matcher(path).replaceAll(":$1");
    }

    protected void handleRequest(RoutingContext ctx) {
        if (isSuspended()) {
            ctx.response().setStatusCode(503);
            ctx.end();
            return;
        }

        final Vertx vertx = ctx.vertx();
        final Exchange exchange = toExchange(ctx);

        //
        // We do not know if any of the processing logic of the route is synchronous or not so we
        // need to process the request on a thread on the Vert.x worker pool.
        //
        // As example, assuming the platform-http component is configured as the transport provider
        // for the rest dsl, then the following code may result in a blocking operation that could
        // block Vert.x event-loop for too long if the target service takes long to respond, as
        // example in case the service is a knative service scaled to zero that could take some time
        // to become available:
        //
        //     rest("/results")
        //         .get("/{id}")
        //         .route()
        //             .removeHeaders("*", "CamelHttpPath")
        //             .to("rest:get:?bridgeEndpoint=true");
        //

        if (getEndpoint().isHttpProxy()) {
            exchange.getExchangeExtension().setStreamCacheDisabled(true);
            final MultiMap httpHeaders = ctx.request().headers();
            exchange.getMessage().setHeader(Exchange.HTTP_HOST, httpHeaders.get("Host"));
            exchange.getMessage().removeHeader("Proxy-Connection");
        }
        vertx.executeBlocking(
                promise -> {
                    try {
                        createUoW(exchange);
                    } catch (Exception e) {
                        promise.fail(e);
                        return;
                    }

                    getAsyncProcessor().process(exchange, c -> {
                        promise.complete();
                    });
                },
                false,
                result -> {
                    Throwable failure = null;
                    try {
                        if (result.succeeded()) {
                            try {
                                writeResponse(ctx, exchange, getEndpoint().getHeaderFilterStrategy(), muteExceptions);
                            } catch (Exception e) {
                                failure = e;
                            }
                        } else {
                            failure = result.cause();
                        }

                        if (failure != null) {
                            getExceptionHandler().handleException(
                                    "Failed handling platform-http endpoint " + getEndpoint().getPath(),
                                    failure);
                            ctx.fail(failure);
                        }
                    } finally {
                        doneUoW(exchange);
                        releaseExchange(exchange, false);
                    }
                });
    }

    protected Exchange toExchange(RoutingContext ctx) {
        final Exchange exchange = createExchange(false);
        exchange.setPattern(ExchangePattern.InOut);

        final Message in = toCamelMessage(ctx, exchange);
        final String charset = ctx.parsedHeaders().contentType().parameter("charset");
        if (charset != null) {
            exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, charset);
            in.setHeader(Exchange.HTTP_CHARACTER_ENCODING, charset);
        }

        User user = ctx.user();
        if (user != null) {
            in.setHeader(VertxPlatformHttpConstants.AUTHENTICATED_USER, user);
        }

        return exchange;
    }

    protected Message toCamelMessage(RoutingContext ctx, Exchange exchange) {
        final Message result = exchange.getIn();

        final HeaderFilterStrategy headerFilterStrategy = getEndpoint().getHeaderFilterStrategy();
        populateCamelHeaders(ctx, result.getHeaders(), exchange, headerFilterStrategy);
        final String mimeType = ctx.parsedHeaders().contentType().value();
        final boolean isMultipartFormData = "multipart/form-data".equals(mimeType);
        if ("application/x-www-form-urlencoded".equals(mimeType) || isMultipartFormData) {
            final MultiMap formData = ctx.request().formAttributes();
            final Map<String, Object> body = new HashMap<>();
            for (String key : formData.names()) {
                for (String value : formData.getAll(key)) {
                    if (headerFilterStrategy != null
                            && !headerFilterStrategy.applyFilterToExternalHeaders(key, value, exchange)) {
                        appendHeader(result.getHeaders(), key, value);
                        appendHeader(body, key, value);
                    }
                }
            }

            if (!body.isEmpty()) {
                result.setBody(body);
            }

            if (isMultipartFormData) {
                populateAttachments(ctx.fileUploads(), result);
            }
        } else {
            final RequestBody requestBody = ctx.body();
            final Buffer body = requestBody.buffer();
            if (body != null) {
                result.setBody(body);
            } else {
                result.setBody(null);
            }
        }
        return result;
    }

    protected void populateAttachments(List<FileUpload> uploads, Message message) {
        for (FileUpload upload : uploads) {
            final String name = upload.name();
            final String fileName = upload.fileName();

            LOGGER.trace("HTTP attachment {} = {}", name, fileName);

            // is the file name accepted
            boolean accepted = true;

            if (fileNameExtWhitelist != null) {
                String ext = FileUtil.onlyExt(fileName);
                if (ext != null) {
                    ext = ext.toLowerCase(Locale.US);
                    if (!fileNameExtWhitelist.equals("*") && !fileNameExtWhitelist.contains(ext)) {
                        accepted = false;
                    }
                }
            }
            if (accepted) {
                final File localFile = new File(upload.uploadedFileName());
                final AttachmentMessage attachmentMessage = message.getExchange().getMessage(AttachmentMessage.class);
                attachmentMessage.addAttachment(fileName, new DataHandler(new CamelFileDataSource(localFile, fileName)));
            } else {
                LOGGER.debug(
                        "Cannot add file as attachment: {} because the file is not accepted according to fileNameExtWhitelist: {}",
                        fileName, fileNameExtWhitelist);
            }
        }
    }
}
