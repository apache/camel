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
package org.apache.camel.telemetry.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.telemetry.Op;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.TagConstants;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

public abstract class AbstractHttpSpanDecorator extends AbstractSpanDecorator {

    public static final String POST_METHOD = "POST";
    public static final String GET_METHOD = "GET";

    public String getHttpMethod(Exchange exchange, Endpoint endpoint) {
        // 1. Use method provided in header.
        Object method = exchange.getIn().getHeader(Exchange.HTTP_METHOD);
        if (method instanceof String str) {
            return str;
        } else if (method instanceof Enum<?> enumValue) {
            return enumValue.name();
        } else if (method != null) {
            return exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange, method);
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
    public String getOperationName(Exchange exchange, Endpoint endpoint) {
        // Based on HTTP component documentation:
        return getHttpMethod(exchange, endpoint);
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String httpUrl = getHttpURL(exchange, endpoint);
        if (httpUrl != null) {
            span.setTag(TagConstants.HTTP_URL, httpUrl);
        }
        span.setTag(TagConstants.HTTP_METHOD, getHttpMethod(exchange, endpoint));
    }

    protected String getHttpURL(Exchange exchange, Endpoint endpoint) {
        // the url is presented the same way as the camel.uri tag
        Object url = exchange.getIn().getHeader(Exchange.HTTP_URL);
        if (url instanceof String str) {
            return URISupport.sanitizeUri(str);
        } else {
            Object uri = exchange.getIn().getHeader(Exchange.HTTP_URI);
            if (uri instanceof String str) {
                return URISupport.sanitizeUri(str);
            } else {
                // Try to obtain from endpoint
                return URISupport.sanitizeUri(getHttpAddress(endpoint));
            }
        }
    }

    /**
     * The HTTP address the endpoint sends to, without the endpoint options.
     */
    private static String getHttpAddress(Endpoint endpoint) {
        // the service url of an HTTP endpoint is its HTTP address with the query parameters that are sent
        if (endpoint instanceof EndpointServiceLocation esl) {
            String url = esl.getServiceUrl();
            if (isHttpAddress(url)) {
                return url;
            }
        }
        // otherwise take the endpoint uri, without the component scheme in front of the HTTP address
        // (such as netty-http:http://host or vertx-http://https://host) and without the endpoint options
        String uri = endpoint.getEndpointUri();
        if (!isHttpAddress(uri)) {
            uri = StringHelper.after(uri, ":");
            if (uri != null && uri.startsWith("//")) {
                uri = uri.substring(2);
            }
        }
        return isHttpAddress(uri) ? URISupport.stripQuery(uri) : null;
    }

    private static boolean isHttpAddress(String uri) {
        return uri != null && (uri.startsWith("http://") || uri.startsWith("https://"));
    }

    @Override
    public void afterTracingEvent(Span span, Exchange exchange) {
        super.afterTracingEvent(span, exchange);

        Message message = exchange.getMessage();
        if (message != null) {
            Integer responseCode = message.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
            if (responseCode != null) {
                span.setTag(TagConstants.HTTP_STATUS, responseCode.toString());
            }
        }
    }

    @Override
    public String getSpanKind(String operationName) {
        if (Op.EVENT_RECEIVED.name().equals(operationName)) {
            return "SERVER";
        } else if (Op.EVENT_SENT.name().equals(operationName)) {
            return "CLIENT";
        } else {
            return "INTERNAL";
        }
    }
}
