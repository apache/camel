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
package org.apache.camel.component.odata;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.http.HttpConstants;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.URISupport;

public class ODataProducer extends DefaultProducer {

    private final ODataEndpoint endpoint;
    private Producer httpProducer;

    public ODataProducer(ODataEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        URI uri = endpoint.getHttpUri();
        String httpUri = uri.toString();

        if (httpUri.contains("?")) {
            httpUri += "&throwExceptionOnFailure=true";
        } else {
            httpUri += "?throwExceptionOnFailure=true";
        }

        ODataConfiguration config = endpoint.getConfiguration();
        Map<String, Object> httpParameters = new LinkedHashMap<>();

        if (config != null) {
            SSLContextParameters sslParams = endpoint.getSslContextParameters();
            if (sslParams == null && endpoint.isUseGlobalSslContextParameters()) {
                sslParams = endpoint.getCamelContext().getSSLContextParameters();
            }
            if (sslParams != null) {
                httpParameters.put("sslContextParameters", sslParams);
            }
        }

        Endpoint httpEndpoint = endpoint.getCamelContext()
                .getEndpoint(httpUri, httpParameters);

        httpProducer = httpEndpoint.createProducer();
        ServiceHelper.startService(httpProducer);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(httpProducer);
        httpProducer = null;

        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        ODataOperation operation = exchange.getMessage().getHeader(
                ODataConstants.OPERATION,
                endpoint.getConfiguration().getOperation(),
                ODataOperation.class);

        String targetUri = buildTargetUri(exchange, operation);

        Exchange httpExchange = endpoint.createExchange();

        MessageHelper.copyHeaders(exchange.getMessage(), httpExchange.getMessage(), true);

        // Remove HTTP routing headers inherited from the incoming exchange.
        // The OData producer builds the target URI itself and must not allow
        // upstream HTTP headers to override the configured OData endpoint.
        httpExchange.getMessage().removeHeader(Exchange.HTTP_URI);
        httpExchange.getMessage().removeHeader(Exchange.HTTP_PATH);
        httpExchange.getMessage().removeHeader(Exchange.HTTP_QUERY);
        httpExchange.getMessage().removeHeader(Exchange.HTTP_RAW_QUERY);
        httpExchange.getMessage().removeHeader("CamelRestHttpUri");

        // Sanitize internal OData control headers before delegation
        httpExchange.getMessage().removeHeader(ODataConstants.OPERATION);
        httpExchange.getMessage().removeHeader(ODataConstants.KEY);
        httpExchange.getMessage().removeHeader(ODataConstants.FILTER);
        httpExchange.getMessage().removeHeader(ODataConstants.SELECT);
        httpExchange.getMessage().removeHeader(ODataConstants.EXPAND);
        httpExchange.getMessage().removeHeader(ODataConstants.ORDER_BY);
        httpExchange.getMessage().removeHeader(ODataConstants.TOP);
        httpExchange.getMessage().removeHeader(ODataConstants.SKIP);

        // Manage Authentication Headers Precedence
        String authHeader = exchange.getMessage().getHeader("Authorization", String.class);
        ODataConfiguration config = endpoint.getConfiguration();

        if (authHeader == null && config != null) {
            if ("Basic".equalsIgnoreCase(config.getAuthMethod())
                    && config.getAuthUsername() != null
                    && config.getAuthPassword() != null) {
                String credentials = config.getAuthUsername() + ":" + config.getAuthPassword();
                String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                httpExchange.getMessage().setHeader("Authorization", "Basic " + encoded);
            } else if (config.getAuthBearerToken() != null) {
                httpExchange.getMessage().setHeader("Authorization", "Bearer " + config.getAuthBearerToken());
            }
        }

        // Standard Dynamic URI headers for HttpProducer
        httpExchange.getMessage().setHeader(Exchange.HTTP_URI, targetUri);
        if (targetUri.contains("?")) {
            String queryString = targetUri.substring(targetUri.indexOf('?') + 1);
            httpExchange.getMessage().setHeader(Exchange.HTTP_QUERY, queryString);
        }

        switch (operation) {
            case READ_SET, READ_ENTRY -> {
                httpExchange.getMessage().setHeader(HttpConstants.HTTP_METHOD, "GET");
            }
            case CREATE -> {
                httpExchange.getMessage().setHeader(HttpConstants.HTTP_METHOD, "POST");
                httpExchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                httpExchange.getMessage().setBody(ODataHelper.toJson(exchange.getMessage().getBody()));
            }
            case UPDATE -> {
                httpExchange.getMessage().setHeader(HttpConstants.HTTP_METHOD, "PATCH");
                httpExchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                httpExchange.getMessage().setBody(ODataHelper.toJson(exchange.getMessage().getBody()));
            }
            case DELETE -> {
                httpExchange.getMessage().setHeader(HttpConstants.HTTP_METHOD, "DELETE");
            }
        }

        httpExchange.getMessage().setHeader("Accept", "application/json");

        String etag = exchange.getMessage().getHeader(ODataConstants.ETAG, String.class);
        if (etag != null) {
            httpExchange.getMessage().setHeader("If-Match", etag);
            httpExchange.getMessage().removeHeader(ODataConstants.ETAG);
        }

        httpProducer.process(httpExchange);

        Exception cause = httpExchange.getException();
        if (cause != null) {
            exchange.setException(cause);
            return;
        }

        MessageHelper.copyHeaders(httpExchange.getMessage(), exchange.getMessage(), true);

        Integer responseCode = httpExchange.getMessage().getHeader(
                Exchange.HTTP_RESPONSE_CODE, Integer.class);

        if (responseCode != null) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, responseCode);
        }

        String responseEtag = httpExchange.getMessage().getHeader("ETag", String.class);

        String body = httpExchange.getMessage().getBody(String.class);

        if ((responseCode != null && responseCode == 204) || body == null || body.isBlank()) {
            if (responseEtag != null) {
                exchange.getMessage().setHeader(ODataConstants.ETAG, responseEtag);
            }
            exchange.getMessage().setBody(null);
            return;
        }

        Map<String, Object> response = ODataHelper.parseJsonObject(body);

        Object count = response.get("@odata.count");
        if (count instanceof Number number) {
            exchange.getMessage().setHeader(ODataConstants.COUNT, number.longValue());
        }

        Object nextLink = response.get("@odata.nextLink");
        if (nextLink != null) {
            exchange.getMessage().setHeader(ODataConstants.NEXT_LINK, nextLink.toString());
        }

        if (responseEtag == null) {
            Object odataEtag = response.get("@odata.etag");
            if (odataEtag != null) {
                responseEtag = odataEtag.toString();
            }
        }

        if (responseEtag != null) {
            exchange.getMessage().setHeader(ODataConstants.ETAG, responseEtag);
        }

        exchange.getMessage().setBody(response);
    }

    private String buildTargetUri(Exchange exchange, ODataOperation operation) throws Exception {
        String base = endpoint.getHttpUri().toString();

        String key = exchange.getMessage().getHeader(ODataConstants.KEY, String.class);

        if ((operation == ODataOperation.READ_ENTRY
                || operation == ODataOperation.UPDATE
                || operation == ODataOperation.DELETE) && key != null && !key.isBlank()) {

            if (key.startsWith("(")) {
                base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
                base += key;
            } else {
                base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
                base += "(" + key + ")";
            }
        }

        Map<String, Object> params = new LinkedHashMap<>(endpoint.getOdataQueryParams());
        ODataConfiguration config = endpoint.getConfiguration();

        // 1. $filter
        String filter = exchange.getMessage().getHeader(ODataConstants.FILTER, config.getFilter(), String.class);
        if (filter != null && !filter.isBlank()) {
            params.put("$filter", filter);
        }

        // 2. $select
        String select = exchange.getMessage().getHeader(ODataConstants.SELECT, config.getSelect(), String.class);
        if (select != null && !select.isBlank()) {
            params.put("$select", select);
        }

        // 3. $expand
        String expand = exchange.getMessage().getHeader(ODataConstants.EXPAND, config.getExpand(), String.class);
        if (expand != null && !expand.isBlank()) {
            params.put("$expand", expand);
        }

        // 4. $orderby
        String orderBy = exchange.getMessage().getHeader(ODataConstants.ORDER_BY, config.getOrderBy(), String.class);
        if (orderBy != null && !orderBy.isBlank()) {
            params.put("$orderby", orderBy);
        }

        // 5. $top
        Integer top = exchange.getMessage().getHeader(ODataConstants.TOP, config.getTop(), Integer.class);
        if (top != null) {
            params.put("$top", top);
        }

        // 6. $skip
        Integer skip = exchange.getMessage().getHeader(ODataConstants.SKIP, config.getSkip(), Integer.class);
        if (skip != null) {
            params.put("$skip", skip);
        }

        // 7. $count
        Boolean count = exchange.getMessage().getHeader(ODataConstants.INCLUDE_COUNT, config.getCount(), Boolean.class);
        if (count != null) {
            params.put("$count", count);
        }

        return appendQueryParameters(base, params);
    }

    private String appendQueryParameters(String uri, Map<String, Object> params) throws Exception {
        if (params.isEmpty()) {
            return uri;
        }

        return URISupport.appendParametersToURI(uri, params);
    }
}
