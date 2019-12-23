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
package org.apache.camel.opentracing.decorators;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

public abstract class AbstractHttpSpanDecorator extends AbstractSpanDecorator {

    public static final String POST_METHOD = "POST";
    public static final String GET_METHOD = "GET";

    @Override
    public String getOperationName(Exchange exchange, Endpoint endpoint) {
        // Based on HTTP component documentation:
        return getHttpMethod(exchange, endpoint);
    }

    public static String getHttpMethod(Exchange exchange, Endpoint endpoint) {
        // 1. Use method provided in header.
        Object method = exchange.getIn().getHeader(Exchange.HTTP_METHOD);
        if (method instanceof String) {
            return (String)method;
        }

        // 2. GET if query string is provided in header.
        if (exchange.getIn().getHeader(Exchange.HTTP_QUERY) != null) {
            return GET_METHOD;
        }

        // 3. GET if endpoint is configured with a query string.
        if (endpoint.getEndpointUri().indexOf('?') != -1) {
            return GET_METHOD;
        }

        // 4. POST if there is data to send (body is not null).
        if (exchange.getIn().getBody() != null) {
            return POST_METHOD;
        }

        // 5. GET otherwise.
        return GET_METHOD;
    }

    @Override
    public void pre(Span span, Exchange exchange, Endpoint endpoint) {
        super.pre(span, exchange, endpoint);

        String httpUrl = getHttpURL(exchange, endpoint);
        if (httpUrl != null) {
            span.setTag(Tags.HTTP_URL.getKey(), httpUrl);
        }

        span.setTag(Tags.HTTP_METHOD.getKey(), getHttpMethod(exchange, endpoint));
    }

    protected String getHttpURL(Exchange exchange, Endpoint endpoint) {
        Object url = exchange.getIn().getHeader(Exchange.HTTP_URL);
        if (url instanceof String) {
            return (String) url;
        } else {
            Object uri = exchange.getIn().getHeader(Exchange.HTTP_URI);
            if (uri instanceof String) {
                return (String) uri;
            } else {
                // Try to obtain from endpoint
                int index = endpoint.getEndpointUri().lastIndexOf("http:");
                if (index != -1) {
                    return endpoint.getEndpointUri().substring(index);
                }
            }
        }
        return null;
    }

    @Override
    public void post(Span span, Exchange exchange, Endpoint endpoint) {
        super.post(span, exchange, endpoint);

        Message message = exchange.getMessage();
        if (message != null) {
            Integer responseCode = message.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
            if (responseCode != null) {
                span.setTag(Tags.HTTP_STATUS.getKey(), responseCode);
            }
        }
    }
}
