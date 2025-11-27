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
package org.apache.camel.component.graphql;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.http.HttpUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphqlProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GraphqlProducer.class);

    private static final Integer OK_RESPONSE_CODE = 200;
    private static final String OK_STATUS_RANGE = "200-299";

    private HttpClient httpClient;
    private boolean closeHttpClient;

    public GraphqlProducer(GraphqlEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        httpClient = getEndpoint().getHttpClient();
        if (httpClient == null) {
            httpClient = getEndpoint().createHttpClient();
            closeHttpClient = true;
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (closeHttpClient && httpClient instanceof CloseableHttpClient chc) {
            IOHelper.close(chc);
        }
    }

    @Override
    public GraphqlEndpoint getEndpoint() {
        return (GraphqlEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            URI httpUri = getEndpoint().getHttpUri();
            String requestBody = buildRequestBody(getQuery(exchange), getEndpoint().getOperationName(),
                    getVariables(exchange));
            try (HttpEntity requestEntity = new StringEntity(requestBody, ContentType.APPLICATION_JSON)) {
                HttpPost httpPost = new HttpPost(httpUri);
                httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                httpPost.setHeader(HttpHeaders.ACCEPT, "application/json");
                httpPost.setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip");
                httpPost.setEntity(requestEntity);
                populateRequestHeaders(exchange, httpPost);

                httpClient.execute(httpPost, httpResponse -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Finished executing http: {} method: {}", httpUri, HttpPost.METHOD_NAME);
                    }
                    int responseCode = httpResponse.getCode();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Http responseCode: {}", responseCode);
                    }
                    if (!getEndpoint().isThrowExceptionOnFailure()) {
                        // if we do not use failed exception then populate response for all response codes
                        populateResponse(exchange, httpResponse, getEndpoint().getHeaderFilterStrategy(), responseCode);
                    } else {
                        boolean ok = HttpHelper.isStatusCodeOk(responseCode, OK_STATUS_RANGE);
                        if (ok) {
                            // only populate response for OK response
                            populateResponse(exchange, httpResponse, getEndpoint().getHeaderFilterStrategy(), responseCode);
                        } else {
                            // also store response code when throwing exception
                            populateResponseCode(exchange.getMessage(), httpResponse, responseCode);

                            // operation failed so populate exception to throw
                            exchange.setException(populateHttpOperationFailedException(exchange, httpResponse, responseCode));
                        }
                    }
                    return null;
                });
            }
        } catch (Exception e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    private void populateRequestHeaders(Exchange exchange, HttpPost httpRequest) {
        HeaderFilterStrategy strategy = getEndpoint().getHeaderFilterStrategy();
        final TypeConverter tc = exchange.getContext().getTypeConverter();
        for (Map.Entry<String, Object> entry : exchange.getMessage().getHeaders().entrySet()) {
            String key = entry.getKey();
            // we should not add known headers

            // skip known headers from graphql
            boolean skip = getEndpoint().getQueryHeader() != null && key.equalsIgnoreCase(getEndpoint().getQueryHeader())
                    || getEndpoint().getVariablesHeader() != null && key.equalsIgnoreCase(getEndpoint().getVariablesHeader());
            if (skip) {
                continue;
            }

            Object headerValue = entry.getValue();
            if (headerValue != null) {
                if (headerValue instanceof String || headerValue instanceof Integer || headerValue instanceof Long
                        || headerValue instanceof Boolean || headerValue instanceof Date) {
                    // optimise for common types
                    String value = headerValue.toString();
                    if (!strategy.applyFilterToCamelHeaders(key, value, exchange)) {
                        httpRequest.addHeader(key, value);
                    }
                    continue;
                }

                // use an iterator as there can be multiple values. (must not use a delimiter, and allow empty values)
                final Iterator<?> it = ObjectHelper.createIterator(headerValue, null, true);

                HttpUtil.applyHeader(strategy, exchange, it, tc, key,
                        (multiValues, prev) -> applyHeader(httpRequest, key, multiValues, prev));
            }
        }
    }

    private static void applyHeader(HttpUriRequest httpRequest, String key, List<String> multiValues, String prev) {
        // add the value(s) as a http request header
        if (multiValues != null) {
            // use the default toString of a ArrayList to create in the form [xxx, yyy]
            // if multi valued, for a single value, then just output the value as is
            String s = multiValues.size() > 1 ? multiValues.toString() : multiValues.get(0);
            httpRequest.addHeader(key, s);
        } else if (prev != null) {
            httpRequest.addHeader(key, prev);
        }
    }

    private static void populateResponseCode(Message message, ClassicHttpResponse httpResponse, int responseCode) {
        // optimize for 200 response code as the boxing is outside the cached integers
        if (responseCode == 200) {
            message.setHeader(Exchange.HTTP_RESPONSE_CODE, OK_RESPONSE_CODE);
        } else {
            message.setHeader(Exchange.HTTP_RESPONSE_CODE, responseCode);
        }
        if (httpResponse.getReasonPhrase() != null) {
            message.setHeader(Exchange.HTTP_RESPONSE_TEXT, httpResponse.getReasonPhrase());
        }
    }

    protected Exception populateHttpOperationFailedException(
            Exchange exchange, ClassicHttpResponse httpResponse, int responseCode)
            throws IOException, ParseException {
        Exception answer;

        String statusText = httpResponse.getReasonPhrase() != null ? httpResponse.getReasonPhrase() : null;
        Map<String, String> headers = extractResponseHeaders(httpResponse.getHeaders());

        Object responseBody = EntityUtils.toString(httpResponse.getEntity());

        // make a defensive copy of the response body in the exception so its detached from the cache
        String copy = null;
        if (responseBody != null) {
            copy = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, responseBody);
        }

        Header locationHeader = httpResponse.getFirstHeader("location");
        String uri = getEndpoint().getHttpUri().toString();
        if (locationHeader != null && responseCode >= 300 && responseCode < 400) {
            answer = new HttpOperationFailedException(
                    uri, responseCode, statusText, locationHeader.getValue(), headers, copy);
        } else {
            answer = new HttpOperationFailedException(uri, responseCode, statusText, null, headers, copy);
        }

        return answer;
    }

    protected void populateResponse(
            Exchange exchange, ClassicHttpResponse httpResponse,
            HeaderFilterStrategy strategy, int responseCode)
            throws IOException, ParseException {

        Message answer = exchange.getMessage();
        populateResponseCode(answer, httpResponse, responseCode);

        // We just make the out message is not create when extractResponseBody throws exception
        Object responseBody = EntityUtils.toString(httpResponse.getEntity());
        answer.setBody(responseBody);

        // optimize to walk headers with an iterator which does not create a new array as getAllHeaders does
        boolean found = false;
        Iterator<Header> it = httpResponse.headerIterator();
        while (it.hasNext()) {
            Header header = it.next();
            String name = header.getName();
            String value = header.getValue();
            if (!found && name.equalsIgnoreCase("content-type")) {
                name = Exchange.CONTENT_TYPE;
                exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, IOHelper.getCharsetNameFromContentType(value));
                found = true;
            }
            // use http helper to extract parameter value as it may contain multiple values
            Object extracted = HttpHelper.extractHttpParameterValue(value);
            if (strategy != null && !strategy.applyFilterToExternalHeaders(name, extracted, exchange)) {
                HttpHelper.appendHeader(answer.getHeaders(), name, extracted);
            }
        }
    }

    /**
     * Extracts the response headers
     *
     * @param  responseHeaders the headers
     * @return                 the extracted headers or an empty map if no headers existed
     */
    protected static Map<String, String> extractResponseHeaders(Header[] responseHeaders) {
        if (responseHeaders == null || responseHeaders.length == 0) {
            return Map.of();
        }

        Map<String, String> answer = new HashMap<>();
        for (Header header : responseHeaders) {
            answer.put(header.getName(), header.getValue());
        }

        return answer;
    }

    protected static String buildRequestBody(String query, String operationName, JsonObject variables) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("query", query);
        jsonObject.put("operationName", operationName);
        jsonObject.put("variables", variables != null ? variables : new JsonObject());
        return jsonObject.toJson();
    }

    private String getQuery(Exchange exchange) throws InvalidPayloadException {
        String query = null;
        if (getEndpoint().getQuery() != null) {
            query = getEndpoint().getQuery();
        } else if (getEndpoint().getQueryHeader() != null) {
            query = exchange.getIn().getHeader(getEndpoint().getQueryHeader(), String.class);
        } else {
            query = exchange.getIn().getMandatoryBody(String.class);
        }
        return query;
    }

    private JsonObject getVariables(Exchange exchange) {
        JsonObject variables = null;
        if (getEndpoint().getVariables() != null) {
            variables = getEndpoint().getVariables();
        } else if (getEndpoint().getVariablesHeader() != null) {
            variables = exchange.getIn().getHeader(getEndpoint().getVariablesHeader(), JsonObject.class);
        } else if (exchange.getIn().getBody() instanceof JsonObject) {
            variables = exchange.getIn().getBody(JsonObject.class);
        }
        return variables;
    }
}
