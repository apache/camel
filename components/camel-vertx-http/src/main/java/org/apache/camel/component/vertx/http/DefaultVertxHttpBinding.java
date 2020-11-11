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
package org.apache.camel.component.vertx.http;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

import static org.apache.camel.component.vertx.http.VertxHttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT;

public class DefaultVertxHttpBinding implements VertxHttpBinding {

    @Override
    public HttpRequest<Buffer> prepareHttpRequest(VertxHttpEndpoint endpoint, Exchange exchange) throws Exception {
        VertxHttpConfiguration configuration = endpoint.getConfiguration();
        Message message = exchange.getMessage();

        // Resolve query string from the HTTP_QUERY header or default to those provided on the endpoint HTTP URI
        String queryString = VertxHttpHelper.resolveQueryString(exchange);
        if (ObjectHelper.isEmpty(queryString)) {
            queryString = configuration.getHttpUri().getQuery();
        }

        // Determine the HTTP method to use if not specified in the HTTP_METHOD header
        HttpMethod method = message.getHeader(Exchange.HTTP_METHOD, configuration.getHttpMethod(), HttpMethod.class);
        if (method == null) {
            if (ObjectHelper.isNotEmpty(queryString)) {
                method = HttpMethod.GET;
            } else if (message.getBody() != null) {
                method = HttpMethod.POST;
            } else {
                method = HttpMethod.GET;
            }
        }

        // Resolve the URI to use which is either a combination of headers HTTP_URI & HTTP_PATH or the HTTP URI configured on the endpoint
        URI uri = VertxHttpHelper.resolveHttpURI(exchange);
        if (uri == null) {
            uri = configuration.getHttpUri();
        }

        WebClient webClient = endpoint.getWebClient();
        HttpRequest<Buffer> request;
        if (uri.getPort() != -1) {
            request = webClient.request(method, uri.getPort(), uri.getHost(), uri.getPath());
        } else {
            request = webClient.requestAbs(method, uri.toString());
        }

        // Configure query params
        Map<String, Object> queryParams = URISupport.parseQuery(queryString);
        for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
            request.addQueryParam(entry.getKey(), entry.getValue().toString());
        }

        // Configure basic authentication
        if (ObjectHelper.isNotEmpty(configuration.getBasicAuthUsername())
                && ObjectHelper.isNotEmpty(configuration.getBasicAuthPassword())) {
            request.basicAuthentication(configuration.getBasicAuthUsername(), configuration.getBasicAuthPassword());
        }

        // Configure bearer token authentication
        if (ObjectHelper.isNotEmpty(configuration.getBearerToken())) {
            request.bearerTokenAuthentication(configuration.getBearerToken());
        }

        populateRequestHeaders(exchange, request, configuration.getHeaderFilterStrategy());

        if (configuration.getTimeout() > -1) {
            request.timeout(configuration.getTimeout());
        }

        return request;
    }

    @Override
    public void populateRequestHeaders(Exchange exchange, HttpRequest<Buffer> request, HeaderFilterStrategy strategy) {
        // Ensure the Content-Type header is always added if the corresponding exchange header is present
        String contentType = ExchangeHelper.getContentType(exchange);
        if (ObjectHelper.isNotEmpty(contentType)) {
            request.putHeader(Exchange.CONTENT_TYPE, contentType);
        }

        // Transfer exchange headers to the HTTP request while applying the filter strategy
        Message message = exchange.getMessage();
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            String headerValue = message.getHeader(entry.getKey(), String.class);
            if (strategy != null && !strategy.applyFilterToCamelHeaders(entry.getKey(), headerValue, exchange)) {
                request.putHeader(entry.getKey(), headerValue);
            }
        }
    }

    @Override
    public void handleResponse(VertxHttpEndpoint endpoint, Exchange exchange, AsyncResult<HttpResponse<Buffer>> response)
            throws Exception {
        HttpResponse<Buffer> result = response.result();
        if (response.succeeded()) {
            Message message = exchange.getMessage();
            VertxHttpConfiguration configuration = endpoint.getConfiguration();
            boolean statusCodeOk = HttpHelper.isStatusCodeOk(result.statusCode(), configuration.getOkStatusCodeRange());

            if ((!configuration.isThrowExceptionOnFailure()) || (configuration.isThrowExceptionOnFailure() && statusCodeOk)) {
                populateResponseHeaders(exchange, result, configuration.getHeaderFilterStrategy());
                message.setBody(processResponseBody(endpoint, exchange, result));
            } else {
                exchange.setException(handleResponseFailure(endpoint, exchange, result));
            }
        } else {
            exchange.setException(response.cause());
        }
    }

    @Override
    public void populateResponseHeaders(Exchange exchange, HttpResponse<Buffer> response, HeaderFilterStrategy strategy) {
        Message message = exchange.getMessage();
        message.setHeader(Exchange.HTTP_RESPONSE_CODE, response.statusCode());
        message.setHeader(Exchange.HTTP_RESPONSE_TEXT, response.statusMessage());

        MultiMap headers = response.headers();

        for (String headerName : headers.names()) {
            String name = headerName;
            String value = headers.get(headerName);
            if (name.equalsIgnoreCase("content-type")) {
                name = Exchange.CONTENT_TYPE;
                exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.getCharsetNameFromContentType(value));
            }
            Object extracted = HttpHelper.extractHttpParameterValue(value);
            if (strategy != null && !strategy.applyFilterToExternalHeaders(name, extracted, exchange)) {
                HttpHelper.appendHeader(message.getHeaders(), name, extracted);
            }
        }
    }

    @Override
    public Object processResponseBody(
            VertxHttpEndpoint endpoint, Exchange exchange, HttpResponse<Buffer> result, boolean exceptionOnly)
            throws Exception {
        Buffer responseBody = result.body();
        if (responseBody != null) {
            String contentType = result.getHeader(Exchange.CONTENT_TYPE);
            if (VertxHttpHelper.isContentTypeMatching(CONTENT_TYPE_JAVA_SERIALIZED_OBJECT, contentType)) {
                boolean transferException = endpoint.getConfiguration().isTransferException();
                boolean allowJavaSerializedObject = endpoint.getComponent().isAllowJavaSerializedObject();

                if (allowJavaSerializedObject || (exceptionOnly && transferException)) {
                    InputStream inputStream
                            = exchange.getContext().getTypeConverter().convertTo(InputStream.class, responseBody.getBytes());
                    if (inputStream != null) {
                        try {
                            return VertxHttpHelper.deserializeJavaObjectFromStream(inputStream);
                        } finally {
                            IOHelper.close(inputStream);
                        }
                    }
                }
            } else {
                return responseBody.getBytes();
            }
        }
        return null;
    }

    public Object processResponseBody(VertxHttpEndpoint endpoint, Exchange exchange, HttpResponse<Buffer> result)
            throws Exception {
        return processResponseBody(endpoint, exchange, result, false);
    }

    @Override
    public Throwable handleResponseFailure(VertxHttpEndpoint endpoint, Exchange exchange, HttpResponse<Buffer> result)
            throws Exception {
        VertxHttpConfiguration configuration = endpoint.getConfiguration();
        Throwable exception;

        Object responseBody = processResponseBody(endpoint, exchange, result, true);
        if (responseBody instanceof Throwable) {
            // Use the exception that was deserialized from the response
            exception = (Throwable) responseBody;
        } else {
            String location = null;
            List<String> redirects = result.followedRedirects();
            if (!redirects.isEmpty()) {
                // Get the last redirect location encountered
                location = redirects.get(redirects.size() - 1);
            }

            Map<String, String> headers = new HashMap<>();
            result.headers().names().forEach(header -> headers.put(header, result.getHeader(header)));

            URI httpURI = VertxHttpHelper.resolveHttpURI(exchange);
            if (httpURI == null) {
                httpURI = configuration.getHttpUri();
            }
            exception = new HttpOperationFailedException(
                    httpURI.toString(), result.statusCode(), result.statusMessage(), location, headers, result.bodyAsString());
        }
        return exception;
    }
}
