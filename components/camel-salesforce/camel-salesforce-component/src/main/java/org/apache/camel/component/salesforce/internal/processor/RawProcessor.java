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
package org.apache.camel.component.salesforce.internal.processor;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.salesforce.SalesforceComponent;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.client.RawClient;
import org.apache.camel.support.service.ServiceHelper;

public class RawProcessor extends AbstractSalesforceProcessor {

    private RawClient rawClient;
    private final PayloadFormat format;

    public RawProcessor(SalesforceEndpoint endpoint) throws SalesforceException {
        super(endpoint);
        format = endpoint.getConfiguration().getFormat();
        if (format == null) {
            throw new IllegalArgumentException("format option must be specified when using the raw operation.");
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        SalesforceComponent component = endpoint.getComponent();
        rawClient = component.createRawClientFor(endpoint);
        ServiceHelper.startService(rawClient);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(rawClient);
        super.doStop();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            StringBuilder path
                    = new StringBuilder(getParameter(SalesforceEndpointConfig.RAW_PATH, exchange, IGNORE_BODY, NOT_OPTIONAL));
            String method = getParameter(SalesforceEndpointConfig.RAW_METHOD, exchange, IGNORE_BODY, NOT_OPTIONAL);
            String params = getParameter(
                    SalesforceEndpointConfig.RAW_QUERY_PARAMETERS, exchange, IGNORE_BODY, IS_OPTIONAL);
            if (params != null) {
                path.append("?");
                for (String p : params.split(",")) {
                    if (!path.toString().endsWith("?")) {
                        path.append("&");
                    }
                    path.append(p).append("=");
                    path.append(urlEncode(exchange.getIn().getHeader(p).toString()));
                }
            }

            InputStream body = exchange.getIn().getBody(InputStream.class);
            rawClient.makeRequest(method, path.toString(), format, body, determineHeaders(exchange),
                    (response, headers, exception) -> {
                        Message in = exchange.getIn();
                        in.getHeaders().putAll(headers);
                        if (exception != null) {
                            exchange.setException(exception);
                        }
                        in.setBody(response);
                        callback.done(false);
                    });
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
        return false;
    }

    @Override
    public Map<String, List<String>> determineHeaders(Exchange exchange) {
        try {
            final Map<String, List<String>> headers = super.determineHeaders(exchange);
            String params = getParameter(
                    SalesforceEndpointConfig.RAW_HTTP_HEADERS, exchange, IGNORE_BODY, IS_OPTIONAL);
            if (params != null) {
                for (String p : params.split(",")) {
                    headers.put(p, Collections.singletonList(exchange.getIn().getHeader(p).toString()));
                }
            }
            return headers;
        } catch (SalesforceException e) {
            throw new RuntimeException(e);
        }
    }

    private String urlEncode(String query) throws UnsupportedEncodingException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        // URLEncoder likes to use '+' for spaces
        encodedQuery = encodedQuery.replace("+", "%20");
        return encodedQuery;
    }
}
