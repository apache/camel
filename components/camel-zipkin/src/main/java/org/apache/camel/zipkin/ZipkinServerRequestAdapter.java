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

import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.URISupport;

import static org.apache.camel.zipkin.ZipkinHelper.getSpanId;

public class ZipkinServerRequestAdapter implements ServerRequestAdapter {

    private final ZipkinEventNotifier eventNotifier;
    private final Exchange exchange;
    private final Endpoint endpoint;
    private final String spanName;
    private final String url;

    public ZipkinServerRequestAdapter(ZipkinEventNotifier eventNotifier, Exchange exchange) {
        this.eventNotifier = eventNotifier;
        this.exchange = exchange;
        this.endpoint = exchange.getFromEndpoint();
        this.spanName = URISupport.sanitizeUri(endpoint.getEndpointKey()).toLowerCase(Locale.US);
        this.url = URISupport.sanitizeUri(endpoint.getEndpointUri());
    }

    @Override
    public TraceData getTraceData() {
        String traceId = exchange.getIn().getHeader("CamelZipkinTraceId", String.class);
        String spanId = exchange.getIn().getHeader("CamelZipkinSpanId", String.class);
        String parentSpanId = exchange.getIn().getHeader("CamelZipkinParentSpanId", String.class);
        if (traceId != null && spanId != null) {
            SpanId span = getSpanId(traceId, spanId, parentSpanId);
            return TraceData.builder().sample(true).spanId(span).build();
        } else {
            return TraceData.builder().build();
        }
    }

    @Override
    public String getSpanName() {
        return spanName;
    }

    @Override
    public Collection<KeyValueAnnotation> requestAnnotations() {
        KeyValueAnnotation key1 = KeyValueAnnotation.create("camel.server.endpoint.url", url);
        KeyValueAnnotation key2 = KeyValueAnnotation.create("camel.server.exchange.id", exchange.getExchangeId());
        KeyValueAnnotation key3 = KeyValueAnnotation.create("camel.server.exchange.pattern", exchange.getPattern().name());

        KeyValueAnnotation key4 = null;
        if (eventNotifier.isIncludeMessageBody()) {
            String body = MessageHelper.extractBodyForLogging(exchange.hasOut() ? exchange.getOut() : exchange.getIn(), "");
            key4 = KeyValueAnnotation.create("camel.server.exchange.message.request.body", body);
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

}
