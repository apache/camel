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

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.component.platform.http.spi.Method;
import org.apache.camel.component.platform.http.spi.UploadAttacher;
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

    private final Router router;
    private final List<Handler<RoutingContext>> handlers;
    private final String fileNameExtWhitelist;
    private final UploadAttacher uploadAttacher;

    private Route route;

    public VertxPlatformHttpConsumer(PlatformHttpEndpoint endpoint, Processor processor, Router router,
                                       List<Handler<RoutingContext>> handlers, UploadAttacher uploadAttacher) {
        super(endpoint, processor);

        this.router = router;
        this.handlers = handlers;

        String list = endpoint.getFileNameExtWhitelist();

        this.fileNameExtWhitelist = list == null ? null : list.toLowerCase(Locale.US);
        this.uploadAttacher = uploadAttacher;
    }

    @Override
    public PlatformHttpEndpoint getEndpoint() {
        return (PlatformHttpEndpoint) super.getEndpoint();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        final PlatformHttpEndpoint endpoint = getEndpoint();
        final String path = endpoint.getPath();

        // Transform from the Camel path param syntax /path/{key} to vert.x web's /path/:key
        final String vertxPathParamPath = PATH_PARAMETER_PATTERN.matcher(path).replaceAll(":$1");
        final Route newRoute = router.route(vertxPathParamPath);

        final Set<Method> methods = Method.parseList(endpoint.getHttpMethodRestrict());
        if (!methods.equals(Method.getAll())) {
            methods.stream().forEach(m -> newRoute.method(HttpMethod.valueOf(m.name())));
        }
        if (endpoint.getConsumes() != null) {
            newRoute.consumes(endpoint.getConsumes());
        }
        if (endpoint.getProduces() != null) {
            newRoute.produces(endpoint.getProduces());
        }

        handlers.forEach(newRoute::handler);

        newRoute.handler(
            ctx -> {
                Exchange exchg = null;
                try {
                    final Exchange exchange = exchg = toExchange(ctx);
                    createUoW(exchange);
                    getAsyncProcessor().process(
                        exchange,
                        doneSync -> writeResponse(ctx, exchange, getEndpoint().getHeaderFilterStrategy()));
                } catch (Exception e) {
                    ctx.fail(e);
                    getExceptionHandler().handleException("Failed handling platform-http endpoint " + path, exchg, e);
                } finally {
                    if (exchg != null) {
                        doneUoW(exchg);
                    }
                }
            });

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

    private Exchange toExchange(RoutingContext ctx) {
        final Exchange exchange = getEndpoint().createExchange();
        Message in = toCamelMessage(ctx, exchange);

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
                uploadAttacher.attachUpload(localFile, fileName, message);
            } else {
                LOGGER.debug(
                    "Cannot add file as attachment: {} because the file is not accepted according to fileNameExtWhitelist: {}",
                    fileName, fileNameExtWhitelist);
            }
        }
    }
}
