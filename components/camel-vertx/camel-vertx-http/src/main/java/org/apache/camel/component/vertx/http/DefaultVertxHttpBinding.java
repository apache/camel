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
import java.util.function.Consumer;

import io.vertx.core.AsyncResult;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.vertx.http.VertxHttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT;

public class DefaultVertxHttpBinding implements VertxHttpBinding {

    @Override
    public HttpRequest<Buffer> prepareHttpRequest(VertxHttpEndpoint endpoint, Exchange exchange) throws Exception {
        VertxHttpConfiguration configuration = endpoint.getConfiguration();
        Message message = exchange.getMessage();

        // Resolve the URI to use which includes query string (similar to camel-http approach)
        // Query string is already encoded in resolveHttpURI to avoid decode/re-encode issues
        URI uri = VertxHttpHelper.resolveHttpURI(exchange, endpoint);

        // Determine the HTTP method to use if not specified in the HTTP_METHOD header
        HttpMethod method = message.getHeader(VertxHttpConstants.HTTP_METHOD, configuration.getHttpMethod(), HttpMethod.class);
        if (method == null) {
            if (ObjectHelper.isNotEmpty(uri.getQuery())) {
                method = HttpMethod.GET;
            } else if (message.getBody() != null) {
                method = HttpMethod.POST;
            } else {
                method = HttpMethod.GET;
            }
        }

        // Use the complete URI string (with query string already encoded)
        // This avoids the decode/re-encode cycle that causes issues with strict HTTP parsers
        WebClient webClient = endpoint.getWebClient();
        HttpRequest<Buffer> request;

        // When using a proxy, use requestAbs() with the full absolute URL. For direct requests (no proxy), use request()
        if (ObjectHelper.isNotEmpty(configuration.getProxyHost())) {
            // Proxy mode - use absolute URL
            request = webClient.requestAbs(method, uri.toString());
        } else {
            // Direct mode - use host/port/path to work with HTTP servers with strict request parsers
            String pathAndQuery = uri.getRawPath();
            if (ObjectHelper.isEmpty(pathAndQuery)) {
                pathAndQuery = "/";
            }
            if (uri.getRawQuery() != null) {
                pathAndQuery = pathAndQuery + "?" + uri.getRawQuery();
            }

            if (uri.getPort() != -1) {
                request = webClient.request(method, uri.getPort(), uri.getHost(), pathAndQuery);
            } else {
                // Default ports: 80 for http, 443 for https
                int port = "https".equals(uri.getScheme()) ? 443 : 80;
                request = webClient.request(method, port, uri.getHost(), pathAndQuery);
            }
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

        populateRequestHeaders(endpoint, exchange, request, configuration.getHeaderFilterStrategy());

        if (configuration.getTimeout() > -1) {
            request.timeout(configuration.getTimeout());
        }

        return request;
    }

    @Override
    public void populateRequestHeaders(
            VertxHttpEndpoint endpoint, Exchange exchange, HttpRequest<Buffer> request, HeaderFilterStrategy strategy) {
        // optimize to use add on MultiMap as putHeader on request does a remove/add
        MultiMap headers = request.headers();

        // Ensure the Content-Type header is always added if the corresponding exchange header is present
        String contentType = ExchangeHelper.getContentType(exchange);
        if (ObjectHelper.isNotEmpty(contentType)) {
            headers.set(VertxHttpConstants.CONTENT_TYPE, contentType);
        }

        // Transfer exchange headers to the HTTP request while applying the filter strategy
        if (strategy != null) {
            final TypeConverter tc = exchange.getContext().getTypeConverter();
            for (Map.Entry<String, Object> entry : exchange.getMessage().getHeaders().entrySet()) {
                String key = entry.getKey();
                Object headerValue = entry.getValue();

                if (endpoint.getConfiguration().isBridgeEndpoint() && request.queryParams().contains(key)) {
                    // Avoid duplicating headers when bridgeEndpoint and query params contains the same header keys
                    continue;
                }

                if (!strategy.applyFilterToCamelHeaders(key, headerValue, exchange)) {
                    String str = tc.convertTo(String.class, headerValue);
                    headers.set(key, str);
                }
            }
        }
    }

    @Override
    public void handleResponse(VertxHttpEndpoint endpoint, Exchange exchange, AsyncResult<HttpResponse<Buffer>> response)
            throws Exception {

        Message message = exchange.getMessage();

        HttpResponse<Buffer> result = response.result();
        if (response.succeeded()) {
            VertxHttpConfiguration configuration = endpoint.getConfiguration();
            boolean ok = endpoint.isStatusCodeOk(result.statusCode());
            if (!configuration.isThrowExceptionOnFailure() || configuration.isThrowExceptionOnFailure() && ok) {
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
        message.setHeader(VertxHttpConstants.HTTP_RESPONSE_CODE, response.statusCode());
        message.setHeader(VertxHttpConstants.HTTP_RESPONSE_TEXT, response.statusMessage());

        MultiMap headers = response.headers();
        if (headers != null && !headers.isEmpty()) {

            // avoid duplicate headers by keeping copy of old headers
            Map<String, Object> copy = new HashMap<>(exchange.getMessage().getHeaders());
            exchange.getMessage().getHeaders().clear();

            headers.forEach(new Consumer<Map.Entry<String, String>>() {
                boolean found;

                @Override
                public void accept(Map.Entry<String, String> entry) {
                    String name = entry.getKey();
                    String value = entry.getValue();
                    if (!found && name.equalsIgnoreCase("content-type")) {
                        found = true;
                        name = VertxHttpConstants.CONTENT_TYPE;
                        exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, IOHelper.getCharsetNameFromContentType(value));
                    }
                    Object extracted = HttpHelper.extractHttpParameterValue(value);
                    if (strategy != null && !strategy.applyFilterToExternalHeaders(name, extracted, exchange)) {
                        HttpHelper.appendHeader(message.getHeaders(), name, extracted);
                    }
                }
            });

            // and only add back old headers if they are not in the HTTP response
            copy.forEach((k, v) -> exchange.getMessage().getHeaders().putIfAbsent(k, v));
        }
    }

    @Override
    public Object processResponseBody(
            VertxHttpEndpoint endpoint, Exchange exchange, HttpResponse<Buffer> result, boolean exceptionOnly)
            throws Exception {
        Buffer responseBody = result.body();
        if (responseBody != null) {
            String contentType = result.getHeader(VertxHttpConstants.CONTENT_TYPE);
            if (CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(contentType)) {
                boolean transferException = endpoint.getConfiguration().isTransferException();
                boolean allowJavaSerializedObject = endpoint.getComponent().isAllowJavaSerializedObject();

                if (allowJavaSerializedObject || exceptionOnly && transferException) {
                    InputStream inputStream
                            = exchange.getContext().getTypeConverter().convertTo(InputStream.class, responseBody.getBytes());
                    if (inputStream != null) {
                        try {
                            return VertxHttpHelper.deserializeJavaObjectFromStream(inputStream,
                                    endpoint.getConfiguration().getDeserializationFilter());
                        } finally {
                            IOHelper.close(inputStream);
                        }
                    }
                }
            } else {
                if (endpoint.getConfiguration().isResponsePayloadAsByteArray()) {
                    return responseBody.getBytes();
                } else {
                    return responseBody;
                }
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

            URI httpURI = VertxHttpHelper.resolveHttpURI(exchange, endpoint);
            exception = new HttpOperationFailedException(
                    httpURI.toString(), result.statusCode(), result.statusMessage(), location, headers, result.bodyAsString());
        }
        return exception;
    }

}
