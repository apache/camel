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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.FileUpload;
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
import org.apache.camel.component.platform.http.cookie.CookieConfiguration;
import org.apache.camel.component.platform.http.cookie.CookieHandler;
import org.apache.camel.component.platform.http.spi.Method;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpSupport.isFormUrlEncoded;
import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpSupport.isMultiPartFormData;
import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpSupport.populateCamelHeaders;
import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpSupport.writeResponse;
import static org.apache.camel.util.CollectionHelper.appendEntry;

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
    private HttpRequestBodyHandler httpRequestBodyHandler;
    private CookieConfiguration cookieConfiguration;

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
        if (!getEndpoint().isHttpProxy() && getEndpoint().isUseStreaming()) {
            httpRequestBodyHandler = new StreamingHttpRequestBodyHandler(router.bodyHandler());
        } else {
            httpRequestBodyHandler = new DefaultHttpRequestBodyHandler(router.bodyHandler());
        }
        if (getEndpoint().isUseCookieHandler()) {
            cookieConfiguration = getEndpoint().getCookieConfiguration();
        }
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

        httpRequestBodyHandler.configureRoute(newRoute);
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
            handleSuspend(ctx);
            return;
        }

        final Vertx vertx = ctx.vertx();
        final Exchange exchange = createExchange(false);
        exchange.setPattern(ExchangePattern.InOut);

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

        // Note: any logic that needs to interrogate HTTP headers not provided by RoutingContext.parsedHeaders, should
        // be done inside of the following onComplete block, to ensure that the HTTP request is fully processed.
        processHttpRequest(exchange, ctx).onComplete(result -> {
            if (result.failed()) {
                handleFailure(exchange, ctx, result.cause());
                return;
            }

            if (getEndpoint().isHttpProxy()) {
                handleProxy(ctx, exchange);
            }

            populateMultiFormData(ctx, exchange.getIn(), getEndpoint().getHeaderFilterStrategy());

            vertx.executeBlocking(() -> processExchange(exchange), false).onComplete(processExchangeResult -> {
                if (processExchangeResult.succeeded()) {
                    writeResponse(ctx, exchange, getEndpoint().getHeaderFilterStrategy(), muteExceptions)
                            .onComplete(writeResponseResult -> {
                                if (writeResponseResult.succeeded()) {
                                    handleExchangeComplete(exchange);
                                } else {
                                    handleFailure(exchange, ctx, writeResponseResult.cause());
                                }
                            });
                } else {
                    handleFailure(exchange, ctx, processExchangeResult.cause());
                }
            });
        });
    }

    private void handleExchangeComplete(Exchange exchange) {
        doneUoW(exchange);
        releaseExchange(exchange, false);
    }

    private void handleFailure(Exchange exchange, RoutingContext ctx, Throwable failure) {
        getExceptionHandler().handleException(
                "Failed handling platform-http endpoint " + getEndpoint().getPath(),
                failure);
        ctx.fail(failure);
        handleExchangeComplete(exchange);
    }

    private Object processExchange(Exchange exchange) throws Exception {
        createUoW(exchange);
        getProcessor().process(exchange);
        return null;
    }

    private static void handleSuspend(RoutingContext ctx) {
        ctx.response().setStatusCode(503);
        ctx.end();
    }

    private static void handleProxy(RoutingContext ctx, Exchange exchange) {
        exchange.getExchangeExtension().setStreamCacheDisabled(true);
        final MultiMap httpHeaders = ctx.request().headers();
        exchange.getMessage().setHeader(Exchange.HTTP_HOST, httpHeaders.get("Host"));
        exchange.getMessage().removeHeader("Proxy-Connection");
    }

    protected Future<Void> processHttpRequest(Exchange exchange, RoutingContext ctx) {
        // reuse existing http message if pooled
        Message in = exchange.getIn();
        if (in instanceof HttpMessage hm) {
            hm.init(exchange, ctx.request(), ctx.response());
        } else {
            in = new HttpMessage(exchange, ctx.request(), ctx.response());
            exchange.setMessage(in);
        }

        final String charset = ctx.parsedHeaders().contentType().parameter("charset");
        if (charset != null) {
            exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, charset);
            in.setHeader(Exchange.HTTP_CHARACTER_ENCODING, charset);
        }

        User user = ctx.user();
        if (user != null) {
            in.setHeader(VertxPlatformHttpConstants.AUTHENTICATED_USER, user);
        }
        if (getEndpoint().isUseCookieHandler()) {
            exchange.setProperty(Exchange.COOKIE_HANDLER, new VertxCookieHandler(ctx));
        }

        return populateCamelMessage(ctx, exchange, in);
    }

    protected Future<Void> populateCamelMessage(RoutingContext ctx, Exchange exchange, Message message) {
        final HeaderFilterStrategy headerFilterStrategy = getEndpoint().getHeaderFilterStrategy();
        populateCamelHeaders(ctx, message.getHeaders(), exchange, headerFilterStrategy);
        return httpRequestBodyHandler.handle(ctx, message);
    }

    private void populateMultiFormData(
            RoutingContext ctx, Message message, HeaderFilterStrategy headerFilterStrategy) {
        final boolean isMultipartFormData = isMultiPartFormData(ctx);
        if (isFormUrlEncoded(ctx) || isMultipartFormData) {
            final MultiMap formData = ctx.request().formAttributes();
            final Map<String, Object> body = new HashMap<>();
            for (String key : formData.names()) {
                for (String value : formData.getAll(key)) {
                    if (headerFilterStrategy != null
                            && !headerFilterStrategy.applyFilterToExternalHeaders(key, value, message.getExchange())) {
                        appendEntry(message.getHeaders(), key, value);
                        appendEntry(body, key, value);
                    }
                }
            }

            if (!body.isEmpty()) {
                message.setBody(body);
            }

            if (isMultipartFormData) {
                populateAttachments(ctx.fileUploads(), message);
            }
        }
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

    class VertxCookieHandler implements CookieHandler {

        private RoutingContext routingContext;

        VertxCookieHandler(RoutingContext routingContext) {
            this.routingContext = routingContext;
        }

        @Override
        public void addCookie(String name, String value) {
            Cookie cookie = Cookie.cookie(name, value)
                    .setPath(cookieConfiguration.getCookiePath())
                    .setDomain(cookieConfiguration.getCookieDomain())
                    .setSecure(cookieConfiguration.isCookieSecure())
                    .setHttpOnly(cookieConfiguration.isCookieHttpOnly())
                    .setSameSite(getSameSite(cookieConfiguration.getCookieSameSite()));
            if (cookieConfiguration.getCookieMaxAge() != null) {
                cookie.setMaxAge(cookieConfiguration.getCookieMaxAge());
            }
            routingContext.response().addCookie(cookie);
        }

        private CookieSameSite getSameSite(CookieConfiguration.CookieSameSite sameSite) {
            for (CookieSameSite css : CookieSameSite.values()) {
                // 'Strict', 'Lax', or 'None'
                if (css.toString().equals(sameSite.getValue())) {
                    return css;
                }
            }
            return null;
        }

        @Override
        public String removeCookie(String name) {
            Cookie cookie = routingContext.response().removeCookie(name);
            return cookie == null ? null : cookie.getValue();
        }

        @Override
        public String getCookieValue(String name) {
            Cookie cookie = routingContext.request().getCookie(name);
            return cookie == null ? null : cookie.getValue();
        }
    }
}
