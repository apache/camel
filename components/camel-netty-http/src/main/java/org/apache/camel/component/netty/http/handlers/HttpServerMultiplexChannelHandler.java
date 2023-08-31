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
package org.apache.camel.component.netty.http.handlers;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.apache.camel.Exchange;
import org.apache.camel.component.netty.http.HttpServerConsumerChannelFactory;
import org.apache.camel.component.netty.http.InboundStreamHttpRequest;
import org.apache.camel.component.netty.http.NettyHttpConfiguration;
import org.apache.camel.component.netty.http.NettyHttpConstants;
import org.apache.camel.component.netty.http.NettyHttpConsumer;
import org.apache.camel.support.RestConsumerContextPathMatcher;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * A multiplex {@link org.apache.camel.component.netty.http.HttpServerInitializerFactory} which keeps a list of
 * handlers, and delegates to the target handler based on the http context path in the incoming request. This is used to
 * allow to reuse the same Netty consumer, allowing to have multiple routes on the same netty
 * {@link io.netty.bootstrap.ServerBootstrap}
 */
@Sharable
public class HttpServerMultiplexChannelHandler extends SimpleChannelInboundHandler<Object>
        implements HttpServerConsumerChannelFactory {

    private static final List<String> METHODS
            = Arrays.asList("GET", "HEAD", "POST", "PUT", "DELETE", "TRACE", "OPTIONS", "CONNECT", "PATCH");

    // use NettyHttpConsumer as logger to make it easier to read the logs as this is part of the consumer
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpConsumer.class);
    private static final AttributeKey<HttpServerChannelHandler> SERVER_HANDLER_KEY = AttributeKey.valueOf("serverHandler");
    private final Set<HttpServerChannelHandler> consumers = new CopyOnWriteArraySet<>();
    private int port;
    private String token;
    private int len;

    public HttpServerMultiplexChannelHandler() {
        // must have default no-arg constructor to allow IoC containers to manage it
    }

    @Override
    public void init(int port) {
        this.port = port;
        this.token = ":" + port;
        this.len = token.length();
    }

    @Override
    public void addConsumer(NettyHttpConsumer consumer) {
        consumers.add(new HttpServerChannelHandler(consumer));
    }

    @Override
    public void removeConsumer(NettyHttpConsumer consumer) {
        for (HttpServerChannelHandler handler : consumers) {
            if (handler.getConsumer() == consumer) {
                consumers.remove(handler);
            }
        }
    }

    @Override
    public int consumers() {
        return consumers.size();
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public ChannelHandler getChannelHandler() {
        return this;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // store request, as this channel handler is created per pipeline
        HttpRequest request;
        if (msg instanceof HttpRequest) {
            request = (HttpRequest) msg;
        } else {
            request = ((InboundStreamHttpRequest) msg).getHttpRequest();
        }

        LOG.debug("Message received: {}", request);

        HttpServerChannelHandler handler = getHandler(request, request.method().name());
        if (handler != null) {

            // special if its an OPTIONS request
            boolean isRestrictedToOptions = handler.getConsumer().getEndpoint().getHttpMethodRestrict() != null
                    && handler.getConsumer().getEndpoint().getHttpMethodRestrict().contains("OPTIONS");
            if ("OPTIONS".equals(request.method().name()) && !isRestrictedToOptions) {
                String allowedMethods
                        = METHODS.stream().filter(m -> isHttpMethodAllowed(request, m)).collect(Collectors.joining(","));
                if (allowedMethods == null && handler.getConsumer().getEndpoint().getHttpMethodRestrict() != null) {
                    allowedMethods = handler.getConsumer().getEndpoint().getHttpMethodRestrict();
                }

                if (allowedMethods == null) {
                    allowedMethods = "GET,HEAD,POST,PUT,DELETE,TRACE,OPTIONS,CONNECT,PATCH";
                }

                if (!allowedMethods.contains("OPTIONS")) {
                    allowedMethods = allowedMethods + ",OPTIONS";
                }

                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
                response.headers().set(NettyHttpConstants.CONTENT_TYPE, "text/plain");
                response.headers().set(Exchange.CONTENT_LENGTH, 0);
                response.headers().set("Allow", allowedMethods);
                ctx.writeAndFlush(response);
                ctx.close();
            } else {
                Attribute<HttpServerChannelHandler> attr = ctx.channel().attr(SERVER_HANDLER_KEY);
                // store handler as attachment
                attr.set(handler);
                if (msg instanceof HttpContent) {
                    // need to hold the reference of content
                    HttpContent httpContent = (HttpContent) msg;
                    httpContent.content().retain();
                }
                handler.channelRead(ctx, msg);
            }
        } else {
            // okay we cannot process this requires so return either 404 or 405.
            // to know if its 405 then we need to check if any other HTTP method would have a consumer for the "same" request
            boolean hasAnyMethod = METHODS.stream().anyMatch(m -> isHttpMethodAllowed(request, m));
            HttpResponse response;
            if (hasAnyMethod) {
                //method match error, return 405
                response = new DefaultHttpResponse(HTTP_1_1, METHOD_NOT_ALLOWED);
            } else {
                // this resource is not found, return 404
                response = new DefaultHttpResponse(HTTP_1_1, NOT_FOUND);
            }
            response.headers().set(NettyHttpConstants.CONTENT_TYPE, "text/plain");
            response.headers().set(Exchange.CONTENT_LENGTH, 0);
            ctx.writeAndFlush(response);
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Attribute<HttpServerChannelHandler> attr = ctx.channel().attr(SERVER_HANDLER_KEY);
        HttpServerChannelHandler handler = attr.get();
        if (handler != null) {
            handler.exceptionCaught(ctx, cause);
        } else {
            if (cause instanceof ClosedChannelException) {
                // The channel is closed so we do nothing here
                LOG.debug("Channel already closed. Ignoring this exception.");
                return;
            } else {
                // we cannot throw the exception here
                LOG.warn(
                        "HttpServerChannelHandler is not found as attachment to handle exception, send 404 back to the client.",
                        cause);
                // Now we just send 404 back to the client
                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, NOT_FOUND);
                response.headers().set(NettyHttpConstants.CONTENT_TYPE, "text/plain");
                response.headers().set(Exchange.CONTENT_LENGTH, 0);
                ctx.writeAndFlush(response);
                ctx.close();
            }
        }
    }

    private boolean isHttpMethodAllowed(HttpRequest request, String method) {
        return getHandler(request, method) != null;
    }

    @SuppressWarnings("unchecked")
    private HttpServerChannelHandler getHandler(HttpRequest request, String method) {
        HttpServerChannelHandler answer = null;

        // quick path to find if there are handlers with HTTP proxy consumers
        for (final HttpServerChannelHandler handler : consumers) {
            NettyHttpConsumer consumer = handler.getConsumer();

            final NettyHttpConfiguration configuration = consumer.getConfiguration();
            if (configuration.isHttpProxy()) {
                return handler;
            }
        }

        // need to strip out host and port etc, as we only need the context-path for matching
        if (method == null) {
            return null;
        }

        String path = request.uri();
        int idx = path.indexOf(token);
        if (idx > -1) {
            path = path.substring(idx + len);
        }
        // use the path as key to find the consumer handler to use
        path = pathAsKey(path);

        List<RestConsumerContextPathMatcher.ConsumerPath<HttpServerChannelHandler>> paths = new ArrayList<>();
        for (final HttpServerChannelHandler handler : consumers) {
            paths.add(new HttpRestConsumerPath(handler));
        }

        RestConsumerContextPathMatcher.ConsumerPath<HttpServerChannelHandler> best
                = RestConsumerContextPathMatcher.matchBestPath(method, path, paths);
        if (best != null) {
            answer = best.getConsumer();
        }

        // fallback to regular matching
        List<HttpServerChannelHandler> candidates = new ArrayList<>();
        if (answer == null) {
            for (final HttpServerChannelHandler handler : consumers) {
                NettyHttpConsumer consumer = handler.getConsumer();

                String consumerPath = consumer.getConfiguration().getPath();
                boolean matchOnUriPrefix = consumer.getEndpoint().getConfiguration().isMatchOnUriPrefix();
                // Just make sure the we get the right consumer path first
                if (RestConsumerContextPathMatcher.matchPath(path, consumerPath, matchOnUriPrefix)) {
                    candidates.add(handler);
                }
            }
        }

        // extra filter by restrict
        candidates = candidates.stream()
                .filter(c -> matchRestMethod(method, c.getConsumer().getEndpoint().getHttpMethodRestrict()))
                .collect(Collectors.toList());
        if (candidates.size() == 1) {
            answer = candidates.get(0);
        }

        return answer;
    }

    private static String pathAsKey(String path) {
        // cater for default path
        if (path == null || path.equals("/")) {
            path = "";
        }

        // strip out query parameters
        path = StringHelper.before(path, "?", path);

        // strip of ending /
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return UnsafeUriCharactersEncoder.encodeHttpURI(path);
    }

    private static boolean matchRestMethod(String method, String restrict) {
        return restrict == null || restrict.toLowerCase(Locale.ENGLISH).contains(method.toLowerCase(Locale.ENGLISH));
    }

}
