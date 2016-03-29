/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.zipkin;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.util.URISupport;

import static org.apache.camel.zipkin.ZipkinHelper.getSpanId;

public class ZipkinServerRequestAdapter implements ServerRequestAdapter {

    private final Exchange exchange;
    private final Endpoint endpoint;
    private final String spanName;
    private final String url;

    public ZipkinServerRequestAdapter(Exchange exchange) {
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
        String id = exchange.getExchangeId();
        String mep = exchange.getPattern().name();

        KeyValueAnnotation key1 = KeyValueAnnotation.create("camel.server.endpoint.url", url);
        KeyValueAnnotation key2 = KeyValueAnnotation.create("camel.server.exchange.id", id);
        KeyValueAnnotation key3 = KeyValueAnnotation.create("camel.server.exchange.pattern", mep);
        return Arrays.asList(key1, key2, key3);
    }

}
