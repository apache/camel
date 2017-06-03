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

import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.KeyValueAnnotation;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.URISupport;

import static org.apache.camel.zipkin.ZipkinHelper.prepareBodyForLogging;

public class ZipkinClientResponseAdaptor implements ClientResponseAdapter {

    private final ZipkinTracer eventNotifier;
    private final Exchange exchange;
    private final Endpoint endpoint;
    private final String url;

    public ZipkinClientResponseAdaptor(ZipkinTracer eventNotifier, Exchange exchange, Endpoint endpoint) {
        this.eventNotifier = eventNotifier;
        this.exchange = exchange;
        this.endpoint = endpoint;
        this.url = URISupport.sanitizeUri(endpoint.getEndpointUri());
    }

    @Override
    public Collection<KeyValueAnnotation> responseAnnotations() {
        KeyValueAnnotation key1 = KeyValueAnnotation.create("camel.client.endpoint.url", url);
        KeyValueAnnotation key2 = KeyValueAnnotation.create("camel.client.exchange.id", exchange.getExchangeId());
        KeyValueAnnotation key3 = KeyValueAnnotation.create("camel.client.exchange.pattern", exchange.getPattern().name());

        KeyValueAnnotation key4 = null;
        if (eventNotifier.isIncludeMessageBody() || eventNotifier.isIncludeMessageBodyStreams()) {
            boolean streams = eventNotifier.isIncludeMessageBodyStreams();
            StreamCache cache = prepareBodyForLogging(exchange, streams);
            String body = MessageHelper.extractBodyForLogging(exchange.hasOut() ? exchange.getOut() : exchange.getIn(), "", streams, streams);
            key4 = KeyValueAnnotation.create("camel.client.exchange.message.response.body", body);
            if (cache != null) {
                cache.reset();
            }
        }

        KeyValueAnnotation key5 = null;
        // lets capture http response code for http based components
        String responseCode = exchange.hasOut() ? exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE, String.class) : exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, String.class);
        if (responseCode != null) {
            key5 = KeyValueAnnotation.create("camel.client.exchange.message.response.code", responseCode);
        }

        List<KeyValueAnnotation> list = new ArrayList<>();
        list.add(key1);
        list.add(key2);
        list.add(key3);
        if (key4 != null) {
            list.add(key4);
        }
        if (key5 != null) {
            list.add(key5);
        }
        return list;
    }
}
