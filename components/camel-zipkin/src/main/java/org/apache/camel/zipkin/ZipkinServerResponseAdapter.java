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
package org.apache.camel.zipkin;

import brave.SpanCustomizer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.URISupport;

import static org.apache.camel.zipkin.ZipkinHelper.prepareBodyForLogging;

class ZipkinServerResponseAdapter {

    private final ZipkinTracer eventNotifier;
    private final Endpoint endpoint;
    private final String url;

    ZipkinServerResponseAdapter(ZipkinTracer eventNotifier, Exchange exchange) {
        this.eventNotifier = eventNotifier;
        this.endpoint = exchange.getFromEndpoint();
        this.url = URISupport.sanitizeUri(endpoint.getEndpointUri());
    }

    void onResponse(Exchange exchange, SpanCustomizer span) {
        String id = exchange.getExchangeId();
        String mep = exchange.getPattern().name();

        span.tag("camel.server.endpoint.url", url);
        span.tag("camel.server.exchange.id", id);
        span.tag("camel.server.exchange.pattern", mep);

        if (exchange.getException() != null) {
            String message = exchange.getException().getMessage();
            span.tag("camel.server.exchange.failure", message);
        } else if (eventNotifier.isIncludeMessageBody() || eventNotifier.isIncludeMessageBodyStreams()) {
            boolean streams = eventNotifier.isIncludeMessageBodyStreams();
            StreamCache cache = prepareBodyForLogging(exchange, streams);
            String body = MessageHelper.extractBodyForLogging(exchange.getMessage(), "", streams, streams);
            span.tag("camel.server.exchange.message.response.body", body);
            if (cache != null) {
                cache.reset();
            }
        }

        // lets capture http response code for http based components
        String responseCode = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, String.class);
        if (responseCode != null) {
            span.tag("camel.server.exchange.message.response.code", responseCode);
        }
    }

}
