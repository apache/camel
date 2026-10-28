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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.regex.Pattern;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;

/**
 * Serves the page of {@code camel tui --web} and its assets from the class path, {@code /tui/web} ({@code /} is its
 * {@code index.html}); a request for the WebSocket path goes on down the pipeline. A path is plain names separated by
 * {@code /}, with no {@code ..}, and only the file types the page uses are served.
 */
final class WebPageHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String ROOT = "/tui/web";
    private static final Pattern PATH = Pattern.compile("(/[A-Za-z0-9._-]+)+");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=UTF-8",
            "js", "application/javascript; charset=UTF-8",
            "css", "text/css; charset=UTF-8",
            "png", "image/png",
            "txt", "text/plain; charset=UTF-8");

    private final String webSocketPath;

    WebPageHandler(String webSocketPath) {
        this.webSocketPath = webSocketPath;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = new QueryStringDecoder(request.uri()).path();
        if (webSocketPath.equalsIgnoreCase(path)) {
            ctx.fireChannelRead(request.retain());
            return;
        }
        FullHttpResponse response;
        if (!HttpMethod.GET.equals(request.method())) {
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED);
        } else {
            byte[] content = load("/".equals(path) ? "/index.html" : path);
            String type = CONTENT_TYPES.get(path.equals("/") ? "html" : path.substring(path.lastIndexOf('.') + 1));
            if (content == null || type == null) {
                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
            } else {
                response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(content));
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, type);
            }
        }
        HttpUtil.setContentLength(response, response.content().readableBytes());
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        HttpUtil.setKeepAlive(response, keepAlive);
        if (keepAlive) {
            ctx.writeAndFlush(response);
        } else {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    /** The resource at a path below the root, or null when there is none, or the path is not a plain one. */
    static byte[] load(String path) {
        if (!PATH.matcher(path).matches() || path.contains("..")) {
            return null;
        }
        try (InputStream in = WebPageHandler.class.getResourceAsStream(ROOT + path)) {
            return in != null ? in.readAllBytes() : null;
        } catch (IOException e) {
            return null;
        }
    }
}
