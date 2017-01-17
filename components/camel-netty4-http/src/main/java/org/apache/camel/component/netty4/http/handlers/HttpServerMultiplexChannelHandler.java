/**
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
package org.apache.camel.component.netty4.http.handlers;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

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
import org.apache.camel.component.netty4.http.HttpServerConsumerChannelFactory;
import org.apache.camel.component.netty4.http.NettyHttpConsumer;
import org.apache.camel.support.RestConsumerContextPathMatcher;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * A multiplex {@link org.apache.camel.component.netty4.http.HttpServerInitializerFactory} which keeps a list of handlers, and delegates to the
 * target handler based on the http context path in the incoming request. This is used to allow to reuse
 * the same Netty consumer, allowing to have multiple routes on the same netty {@link io.netty.bootstrap.ServerBootstrap}
 */
@Sharable
public class HttpServerMultiplexChannelHandler extends SimpleChannelInboundHandler<Object> implements HttpServerConsumerChannelFactory {

    // use NettyHttpConsumer as logger to make it easier to read the logs as this is part of the consumer
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpConsumer.class);
    private static final AttributeKey<HttpServerChannelHandler> SERVER_HANDLER_KEY = AttributeKey.valueOf("serverHandler");
    private final Set<HttpServerChannelHandler> consumers = new CopyOnWriteArraySet<HttpServerChannelHandler>();
    private int port;
    private String token;
    private int len;

    public HttpServerMultiplexChannelHandler() {
        // must have default no-arg constructor to allow IoC containers to manage it
    }

    public void init(int port) {
        this.port = port;
        this.token = ":" + port;
        this.len = token.length();
    }

    public void addConsumer(NettyHttpConsumer consumer) {
        consumers.add(new HttpServerChannelHandler(consumer));
    }

    public void removeConsumer(NettyHttpConsumer consumer) {
        for (HttpServerChannelHandler handler : consumers) {
            if (handler.getConsumer() == consumer) {
                consumers.remove(handler);
            }
        }
    }

    public int consumers() {
        return consumers.size();
    }

    public int getPort() {
        return port;
    }

    public ChannelHandler getChannelHandler() {
        return this;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // store request, as this channel handler is created per pipeline
        HttpRequest request = (HttpRequest) msg;
      
        LOG.debug("Message received: {}", request);

        HttpServerChannelHandler handler = getHandler(request);
        if (handler != null) {
            Attribute<HttpServerChannelHandler> attr = ctx.channel().attr(SERVER_HANDLER_KEY);
            // store handler as attachment
            attr.set(handler);
            if (msg instanceof HttpContent) {
                // need to hold the reference of content
                HttpContent httpContent = (HttpContent) msg;
                httpContent.content().retain();
            }   
            handler.channelRead(ctx, request);
        } else {
            // this resource is not found, so send empty response back
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, NOT_FOUND);
            response.headers().set(Exchange.CONTENT_TYPE, "text/plain");
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
                LOG.warn("HttpServerChannelHandler is not found as attachment to handle exception, send 404 back to the client.", cause);
                // Now we just send 404 back to the client
                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, NOT_FOUND);
                response.headers().set(Exchange.CONTENT_TYPE, "text/plain");
                response.headers().set(Exchange.CONTENT_LENGTH, 0);
                ctx.writeAndFlush(response);
                ctx.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private HttpServerChannelHandler getHandler(HttpRequest request) {
        HttpServerChannelHandler answer = null;

        // need to strip out host and port etc, as we only need the context-path for matching
        String method = request.method().name();
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

        List<RestConsumerContextPathMatcher.ConsumerPath> paths = new ArrayList<RestConsumerContextPathMatcher.ConsumerPath>();
        for (final HttpServerChannelHandler handler : consumers) {
            paths.add(new HttpRestConsumerPath(handler));
        }

        RestConsumerContextPathMatcher.ConsumerPath<HttpServerChannelHandler> best = RestConsumerContextPathMatcher.matchBestPath(method, path, paths);
        if (best != null) {
            answer = best.getConsumer();
        }

        // fallback to regular matching
        if (answer == null) {
            for (final HttpServerChannelHandler handler : consumers) {
                NettyHttpConsumer consumer = handler.getConsumer();
                String consumerPath = consumer.getConfiguration().getPath();
                boolean matchOnUriPrefix = consumer.getEndpoint().getConfiguration().isMatchOnUriPrefix();
                // Just make sure the we get the right consumer path first
                if (RestConsumerContextPathMatcher.matchPath(path, consumerPath, matchOnUriPrefix)) {
                    answer = handler;
                    break;
                }
            }
        }

        return answer;
    }

    private static String pathAsKey(String path) {
        // cater for default path
        if (path == null || path.equals("/")) {
            path = "";
        }

        // strip out query parameters
        int idx = path.indexOf('?');
        if (idx > -1) {
            path = path.substring(0, idx);
        }

        // strip of ending /
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return UnsafeUriCharactersEncoder.encodeHttpURI(path);
    }

}
