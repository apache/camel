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

import java.util.Locale;
import java.util.Map;

import brave.SpanCustomizer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.URISupport;

import static org.apache.camel.zipkin.ZipkinHelper.prepareBodyForLogging;

final class ZipkinClientRequestAdapter {

    private final ZipkinTracer eventNotifier;
    private final String spanName;
    private final String url;

    ZipkinClientRequestAdapter(ZipkinTracer eventNotifier, Endpoint endpoint) {
        this.eventNotifier = eventNotifier;
        this.spanName = URISupport.sanitizeUri(endpoint.getEndpointKey()).toLowerCase(Locale.ROOT);
        this.url = URISupport.sanitizeUri(endpoint.getEndpointUri());
    }

    void onRequest(Exchange exchange, SpanCustomizer span) {
        span.name(spanName);

        span.tag("camel.client.endpoint.url", url);
        span.tag("camel.client.exchange.id", exchange.getExchangeId());
        span.tag("camel.client.exchange.pattern", exchange.getPattern().name());

        if (eventNotifier.isIncludeMessageBody() || eventNotifier.isIncludeMessageBodyStreams()) {
            boolean streams = eventNotifier.isIncludeMessageBodyStreams();
            StreamCache cache = prepareBodyForLogging(exchange, streams);
            String body = MessageHelper.extractBodyForLogging(exchange.getMessage(), "", streams, streams);
            span.tag("camel.client.exchange.message.request.body", body);
            if (cache != null) {
                cache.reset();
            }
        }
        
        Map<String, String> customTags = exchange.getProperty("camel.client.customtags", Map.class);
        if (customTags != null && !customTags.isEmpty()) {
            for (Map.Entry<String, String> tag : customTags.entrySet()) {
                span.tag("custom." + tag.getKey(), tag.getValue());
            }
        }
    }
}
