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

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.netty.NettyConstants;
import org.apache.camel.component.netty.handlers.ClientChannelHandler;
import org.apache.camel.component.netty.http.InboundStreamHttpResponse;
import org.apache.camel.component.netty.http.NettyHttpProducer;

/**
 * Netty HTTP {@link org.apache.camel.component.netty.handlers.ClientChannelHandler} that handles the response combing
 * back from the HTTP server, called by this client.
 */
public class HttpClientChannelHandler extends ClientChannelHandler {
    private final NettyHttpProducer producer;

    public HttpClientChannelHandler(NettyHttpProducer producer) {
        super(producer);
        this.producer = producer;
    }

    @Override
    protected Message getResponseMessage(Exchange exchange, ChannelHandlerContext ctx, Object message) throws Exception {
        HttpResponse response;
        Message answer;

        if (message instanceof FullHttpResponse) {
            FullHttpResponse fullHttpResponse = (FullHttpResponse) message;
            response = fullHttpResponse;
            // use the binding
            answer = producer.getEndpoint().getNettyHttpBinding().toCamelMessage(fullHttpResponse, exchange, producer.getConfiguration());
        } else {
            InboundStreamHttpResponse streamHttpResponse = (InboundStreamHttpResponse) message;
            response = streamHttpResponse.getHttpResponse();
            answer = producer.getEndpoint().getNettyHttpBinding().toCamelMessage(streamHttpResponse, exchange, producer.getConfiguration());
        }

        if (response.status().equals(HttpResponseStatus.CONTINUE)) {
            // need to continue to send the body and will ignore this response
            exchange.setProperty(NettyConstants.NETTY_CLIENT_CONTINUE, true);
        }
        
        if (!HttpUtil.isKeepAlive(response)) {
            // just want to make sure we close the channel if the keepAlive is not true
            exchange.setProperty(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, true);
        }
        // handle cookies
        if (producer.getEndpoint().getCookieHandler() != null) {
            String actualUri = exchange.getIn().getHeader(Exchange.HTTP_URL, String.class);
            URI uri = new URI(actualUri);
            Map<String, List<String>> m = new HashMap<>();
            for (String name : response.headers().names()) {
                m.put(name, response.headers().getAll(name));
            }
            producer.getEndpoint().getCookieHandler().storeCookies(exchange, uri, m);
        }

        return answer;
    }
    
}
