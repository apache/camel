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
package org.apache.camel.zipkin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.internal.Nullable;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.URISupport;

import static org.apache.camel.zipkin.ZipkinHelper.prepareBodyForLogging;

public final class ZipkinClientRequestAdapter implements ClientRequestAdapter {

    private final ZipkinTracer eventNotifier;
    private final String serviceName;
    private final Exchange exchange;
    private final Endpoint endpoint;
    private final String spanName;
    private final String url;

    public ZipkinClientRequestAdapter(ZipkinTracer eventNotifier, String serviceName, Exchange exchange, Endpoint endpoint) {
        this.eventNotifier = eventNotifier;
        this.serviceName = serviceName;
        this.exchange = exchange;
        this.endpoint = endpoint;
        this.spanName = URISupport.sanitizeUri(endpoint.getEndpointKey()).toLowerCase(Locale.US);
        this.url = URISupport.sanitizeUri(endpoint.getEndpointUri());
    }

    @Override
    public String getSpanName() {
        return spanName;
    }

    @Override
    public void addSpanIdToRequest(@Nullable SpanId spanId) {
        if (spanId == null) {
            exchange.getIn().setHeader(ZipkinConstants.SAMPLED, "0");
        } else {
            exchange.getIn().setHeader(ZipkinConstants.SAMPLED, "1");
            exchange.getIn().setHeader(ZipkinConstants.TRACE_ID, IdConversion.convertToString(spanId.traceId));
            exchange.getIn().setHeader(ZipkinConstants.SPAN_ID, IdConversion.convertToString(spanId.spanId));
            if (spanId.nullableParentId() != null) {
                exchange.getIn().setHeader(ZipkinConstants.PARENT_SPAN_ID, IdConversion.convertToString(spanId.nullableParentId()));
            }
        }
    }

    public String getClientServiceName() {
        return serviceName;
    }

    @Override
    public Collection<KeyValueAnnotation> requestAnnotations() {
        KeyValueAnnotation key1 = KeyValueAnnotation.create("camel.client.endpoint.url", url);
        KeyValueAnnotation key2 = KeyValueAnnotation.create("camel.client.exchange.id", exchange.getExchangeId());
        KeyValueAnnotation key3 = KeyValueAnnotation.create("camel.client.exchange.pattern", exchange.getPattern().name());

        KeyValueAnnotation key4 = null;
        if (eventNotifier.isIncludeMessageBody() || eventNotifier.isIncludeMessageBodyStreams()) {
            boolean streams = eventNotifier.isIncludeMessageBodyStreams();
            StreamCache cache = prepareBodyForLogging(exchange, streams);
            String body = MessageHelper.extractBodyForLogging(exchange.hasOut() ? exchange.getOut() : exchange.getIn(), "", streams, streams);
            key4 = KeyValueAnnotation.create("camel.client.exchange.message.request.body", body);
            if (cache != null) {
                cache.reset();
            }
        }

        List<KeyValueAnnotation> list = new ArrayList<>();
        list.add(key1);
        list.add(key2);
        list.add(key3);
        if (key4 != null) {
            list.add(key4);
        }
        return list;
    }

    @Override
    public com.twitter.zipkin.gen.Endpoint serverAddress() {
        return null;
    }
}
