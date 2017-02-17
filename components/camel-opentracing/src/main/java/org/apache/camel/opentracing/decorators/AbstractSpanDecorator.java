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
package org.apache.camel.opentracing.decorators;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.opentracing.SpanDecorator;

/**
 * An abstract base implementation of the {@link SpanDecorator} interface.
 */
public abstract class AbstractSpanDecorator implements SpanDecorator {

    @Override
    public String getOperationName(Exchange exchange, Endpoint endpoint) {
        return endpoint.getEndpointUri();
    }

    @Override
    public void pre(Span span, Exchange exchange, Endpoint endpoint) {
        span.setTag(Tags.COMPONENT.getKey(), CAMEL_COMPONENT + URI.create(endpoint.getEndpointUri()).getScheme());
    }

    @Override
    public void post(Span span, Exchange exchange, Endpoint endpoint) {
        if (exchange.isFailed()) {
            span.setTag(Tags.ERROR.getKey(), true);
            if (exchange.getException() != null) {
                Map<String, String> logEvent = new HashMap<>();
                logEvent.put("event", "error");
                logEvent.put("error.kind", "Exception");
                logEvent.put("message", exchange.getException().getMessage());
                span.log(logEvent);
            }
        }
    }

}
