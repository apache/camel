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

import javax.activation.DataHandler;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.CamelFileDataSource;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.component.platform.http.spi.Method;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultMessage;
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
public class VertxPlatformHttpConsumer extends DefaultConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(VertxPlatformHttpConsumer.class);
    private static final Pattern PATH_PARAMETER_PATTERN = Pattern.compile("\\{([^/}]+)\\}");

    private final List<Handler<RoutingContext>> handlers;
    private final String fileNameExtWhitelist;
    private Set<Method> methods;
    private String path;

    private Route route;

    public VertxPlatformHttpConsumer(
                                     PlatformHttpEndpoint endpoint,
                                     Processor processor,
                                     List<Handler<RoutingContext>> handlers) {
        super(endpoint, processor);

        this.handlers = handlers;
        this.fileNameExtWhitelist
                = endpoint.getFileNameExtWhitelist() == null ? null : endpoint.getFileNameExtWhitelist().toLowerCase(Locale.US);
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
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final VertxPlatformHttpRouter router = VertxPlatformHttpRouter.lookup(getEndpoint().getCamelContext());
        final Route newRoute = router.route(path);

        if (!methods.equals(Method.getAll())) {
            methods.forEach(m -> newRoute.method(HttpMethod.valueOf(m.name())));
        }

        if (getEndpoint().getConsumes() != null) {
            newRoute.consumes(getEndpoint().getConsumes());
        }
        if (getEndpoint().getProduces() != null) {
            newRoute.produces(getEndpoint().getProduces());
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

    @Override
    protected void doSuspend() throws Exception {
        if (route != null) {
            route.disable();
        }
        super.doSuspend();
    }

    @Override
    protected void doResume() throws Exception {
        if (route != null) {
            route.enable();
        }
        super.doResume();
    }

    private String configureEndpointPath(PlatformHttpEndpoint endpoint) {
        String path = endpoint.getPath();
        if (endpoint.isMatchOnUriPrefix()) {
            path += "*";
        }
        // Transform from the Camel path param syntax /path/{key} to vert.x web's /path/:key
        return PATH_PARAMETER_PATTERN.matcher(path).replaceAll(":$1");
    }

    private void handleRequest(RoutingContext ctx) {
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
        // to be come available:
        //
        //     rest("/results")
        //         .get("/{id}")
        //         .route()
        //             .removeHeaders("*", "CamelHttpPath")
        //             .to("rest:get:?bridgeEndpoint=true");
        //
        vertx.executeBlocking(
                promise -> {
                    try {
                        createUoW(exchange);
                    } catch (Exception e) {
                        promise.fail(e);
                        return;
                    }

                    getAsyncProcessor().process(exchange, c -> {
                        if (!exchange.isFailed()) {
                            promise.complete();
                        } else {
                            promise.fail(exchange.getException());
                        }
                    });
                },
                false,
                result -> {
                    Throwable failure = null;
                    try {
                        if (result.succeeded()) {
                            try {
                                writeResponse(ctx, exchange, getEndpoint().getHeaderFilterStrategy());
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
                    }
                });
    }

    private Exchange toExchange(RoutingContext ctx) {
        final Exchange exchange = getEndpoint().createExchange();
        final Message in = toCamelMessage(ctx, exchange);

        final String charset = ctx.parsedHeaders().contentType().parameter("charset");
        if (charset != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, charset);
            in.setHeader(Exchange.HTTP_CHARACTER_ENCODING, charset);
        }

        exchange.setIn(in);
        return exchange;
    }

    private Message toCamelMessage(RoutingContext ctx, Exchange exchange) {
        final Message result = new DefaultMessage(exchange);

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
            result.setBody(body);
            if (isMultipartFormData) {
                populateAttachments(ctx.fileUploads(), result);
            }
        } else {
            // extract body by myself if undertow parser didn't handle and the method is allowed to have one
            // body is extracted as byte[] then auto TypeConverter kicks in
            Method m = Method.valueOf(ctx.request().method().name());
            if (m.canHaveBody()) {
                final Buffer body = ctx.getBody();
                if (body != null) {
                    result.setBody(body.getBytes());
                } else {
                    result.setBody(null);
                }
            } else {
                result.setBody(null);
            }
        }
        return result;
    }

    private void populateAttachments(Set<FileUpload> uploads, Message message) {
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
