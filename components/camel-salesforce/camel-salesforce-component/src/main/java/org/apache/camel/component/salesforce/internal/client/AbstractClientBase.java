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
package org.apache.camel.component.salesforce.internal.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.NoSuchSObjectException;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.SalesforceMultipleChoicesException;
import org.apache.camel.component.salesforce.api.TypeReferences;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpContentResponse;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.ByteBufferContentProvider;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractClientBase extends ServiceSupport
        implements SalesforceSession.SalesforceSessionListener, HttpClientHolder {

    protected static final String APPLICATION_JSON_UTF8 = "application/json;charset=utf-8";
    protected static final String APPLICATION_XML_UTF8 = "application/xml;charset=utf-8";

    private static final int DEFAULT_TERMINATION_TIMEOUT = 10;

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final SalesforceHttpClient httpClient;
    protected final SalesforceSession session;
    protected final SalesforceLoginConfig loginConfig;
    protected final String version;

    protected String accessToken;
    protected String instanceUrl;

    private Phaser inflightRequests;

    private final long terminationTimeout;
    private final ObjectMapper objectMapper;

    public AbstractClientBase(String version, SalesforceSession session, SalesforceHttpClient httpClient,
                              SalesforceLoginConfig loginConfig) {
        this(version, session, httpClient, loginConfig, DEFAULT_TERMINATION_TIMEOUT);
    }

    AbstractClientBase(String version, SalesforceSession session, SalesforceHttpClient httpClient,
                       SalesforceLoginConfig loginConfig, int terminationTimeout) {
        this.version = version;
        this.session = session;
        this.httpClient = httpClient;
        this.loginConfig = loginConfig;
        this.terminationTimeout = terminationTimeout;
        this.objectMapper = JsonUtils.createObjectMapper();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // local cache
        accessToken = session.getAccessToken();
        if (accessToken == null && !loginConfig.isLazyLogin()) {
            try {
                accessToken = session.login(null);
            } catch (SalesforceException e) {
                throw new RuntimeException(e);
            }
        }
        instanceUrl = session.getInstanceUrl();

        // also register this client as a session listener
        session.addListener(this);

        inflightRequests = new Phaser(1);
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();
        if (inflightRequests != null) {
            inflightRequests.arrive();
            if (!inflightRequests.isTerminated()) {
                try {
                    inflightRequests.awaitAdvanceInterruptibly(0, terminationTimeout, TimeUnit.SECONDS);
                } catch (InterruptedException | TimeoutException ignored) {
                    // exception is ignored
                }
            }
        }

        // deregister listener
        session.removeListener(this);
    }

    @Override
    public void onLogin(String accessToken, String instanceUrl) {
        if (!accessToken.equals(this.accessToken)) {
            this.accessToken = accessToken;
            this.instanceUrl = instanceUrl;
        }
    }

    @Override
    public void onLogout() {
        // ignore, if this client makes another request with stale token,
        // SalesforceSecurityListener will auto login!
    }

    protected Request getRequest(HttpMethod method, String url, Map<String, List<String>> headers) {
        return getRequest(method.asString(), url, headers);
    }

    protected Request getRequest(String method, String url, Map<String, List<String>> headers) {
        SalesforceHttpRequest request = (SalesforceHttpRequest) httpClient.newRequest(url).method(method)
                .timeout(session.getTimeout(), TimeUnit.MILLISECONDS);
        request.getConversation().setAttribute(SalesforceSecurityHandler.CLIENT_ATTRIBUTE, this);
        addHeadersTo(request, headers);

        return request;
    }

    protected interface ClientResponseCallback {
        void onResponse(InputStream response, Map<String, String> headers, SalesforceException ex);
    }

    protected void doHttpRequest(final Request request, final ClientResponseCallback callback) {
        // Highly memory inefficient,
        // but buffer the request content to allow it to be replayed for
        // authentication retries
        final ContentProvider content = request.getContent();
        if (content instanceof InputStreamContentProvider) {
            final List<ByteBuffer> buffers = new ArrayList<>();
            for (ByteBuffer buffer : content) {
                buffers.add(buffer);
            }
            request.content(new ByteBufferContentProvider(buffers.toArray(new ByteBuffer[0])));
            buffers.clear();
        }

        inflightRequests.register();
        // execute the request
        request.send(new BufferingResponseListener(httpClient.getMaxContentLength()) {
            @Override
            public void onComplete(Result result) {
                try {
                    Response response = result.getResponse();

                    final Map<String, String> headers = determineHeadersFrom(response);
                    if (result.isFailed()) {

                        // Failure!!!
                        // including Salesforce errors reported as exception
                        // from SalesforceSecurityHandler
                        Throwable failure = result.getFailure();
                        if (failure instanceof SalesforceException) {
                            httpClient.getWorkerPool()
                                    .execute(() -> callback.onResponse(null, headers, (SalesforceException) failure));
                        } else {
                            final String msg = String.format("Unexpected error {%s:%s} executing {%s:%s}", response.getStatus(),
                                    response.getReason(), request.getMethod(),
                                    request.getURI());
                            httpClient.getWorkerPool().execute(() -> callback.onResponse(null, headers,
                                    new SalesforceException(msg, response.getStatus(), failure)));
                        }
                    } else {

                        // HTTP error status
                        final int status = response.getStatus();
                        SalesforceHttpRequest request
                                = (SalesforceHttpRequest) ((SalesforceHttpRequest) result.getRequest()).getConversation()
                                        .getAttribute(SalesforceSecurityHandler.AUTHENTICATION_REQUEST_ATTRIBUTE);

                        if (status == HttpStatus.BAD_REQUEST_400 && request != null) {
                            // parse login error
                            ContentResponse contentResponse
                                    = new HttpContentResponse(response, getContent(), getMediaType(), getEncoding());
                            try {

                                session.parseLoginResponse(contentResponse, getContentAsString());
                                final String msg = String.format("Unexpected Error {%s:%s} executing {%s:%s}", status,
                                        response.getReason(), request.getMethod(), request.getURI());
                                httpClient.getWorkerPool()
                                        .execute(() -> callback.onResponse(null, headers, new SalesforceException(msg, null)));
                            } catch (SalesforceException e) {

                                final String msg = String.format("Error {%s:%s} executing {%s:%s}", status,
                                        response.getReason(), request.getMethod(), request.getURI());
                                httpClient.getWorkerPool().execute(() -> callback.onResponse(null, headers,
                                        new SalesforceException(msg, response.getStatus(), e)));
                            }
                        } else if (status < HttpStatus.OK_200 || status >= HttpStatus.MULTIPLE_CHOICES_300) {
                            // Salesforce HTTP failure!
                            final SalesforceException exception = createRestException(response, getContentAsInputStream());

                            // for APIs that return body on status 400, such as
                            // Composite API we need content as well
                            httpClient.getWorkerPool()
                                    .execute(() -> callback.onResponse(getContentAsInputStream(), headers, exception));
                        } else {

                            // Success!!!
                            httpClient.getWorkerPool()
                                    .execute(() -> callback.onResponse(getContentAsInputStream(), headers, null));
                        }
                    }
                } finally {
                    inflightRequests.arriveAndDeregister();
                }
            }

            @Override
            public InputStream getContentAsInputStream() {
                if (getContent().length == 0) {
                    return null;
                }
                return super.getContentAsInputStream();
            }
        });
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setInstanceUrl(String instanceUrl) {
        this.instanceUrl = instanceUrl;
    }

    @Override
    public HttpClient getHttpClient() {
        return httpClient;
    }

    final List<RestError> readErrorsFrom(
            final InputStream responseContent, final ObjectMapper objectMapper)
            throws IOException {
        final List<RestError> restErrors;
        restErrors = objectMapper.readValue(responseContent, TypeReferences.REST_ERROR_LIST_TYPE);
        return restErrors;
    }

    protected abstract void setAccessToken(Request request);

    protected SalesforceException createRestException(Response response, InputStream responseContent) {
        // get status code and reason phrase
        final int statusCode = response.getStatus();
        String reason = response.getReason();
        if (reason == null || reason.isEmpty()) {
            reason = HttpStatus.getMessage(statusCode);
        }
        try {
            if (responseContent != null && responseContent.available() > 0) {
                final List<String> choices;
                // return list of choices as error message for 300
                if (statusCode == HttpStatus.MULTIPLE_CHOICES_300) {
                    choices = objectMapper.readValue(responseContent, TypeReferences.STRING_LIST_TYPE);
                    return new SalesforceMultipleChoicesException(reason, statusCode, choices);
                } else {
                    List<RestError> restErrors = null;
                    String body = null;
                    try {
                        restErrors = readErrorsFrom(responseContent, objectMapper);
                    } catch (IOException ignored) {
                        // ok. could be a custom response
                    }
                    try {
                        responseContent.reset();
                        body = IOUtils.toString(responseContent, StandardCharsets.UTF_8);
                        responseContent.reset();
                    } catch (Exception t) {
                        log.warn("Unable to reset HTTP response content input stream.");
                    }
                    if (statusCode == HttpStatus.NOT_FOUND_404) {
                        return new NoSuchSObjectException(restErrors);
                    }

                    return new SalesforceException(
                            restErrors, statusCode,
                            "Unexpected error: " + reason + ". See exception `errors` property for detail. " + body,
                            responseContent);
                }
            }
        } catch (IOException | RuntimeException e) {
            // log and ignore
            String msg = "Unexpected Error parsing error response body + [" + responseContent + "] : "
                         + e.getMessage();
            log.warn(msg, e);
        }

        // just report HTTP status info
        return new SalesforceException("Unexpected error: " + reason + ", with content: " + responseContent, statusCode);
    }

    static Map<String, String> determineHeadersFrom(final Response response) {
        final HttpFields headers = response.getHeaders();

        final Map<String, String> answer = new LinkedHashMap<>();
        for (final HttpField header : headers) {
            final String headerName = header.getName();

            if (headerName.startsWith("Sforce")) {
                answer.put(headerName, header.getValue());
            }
        }

        // don't set the response code to "0" and the response text to null if there's a response timeout
        if (response.getStatus() != 0) {
            answer.put(Exchange.HTTP_RESPONSE_CODE, String.valueOf(response.getStatus()));
            answer.put(Exchange.HTTP_RESPONSE_TEXT, response.getReason());
        }

        return answer;
    }

    private static void addHeadersTo(final Request request, final Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }

        request.headers(requestHeaders -> {
            for (Entry<String, List<String>> header : headers.entrySet()) {
                requestHeaders.put(header.getKey(), header.getValue());
            }
        });
    }

    static Map<String, List<String>> determineHeaders(final Exchange exchange) {
        final Message inboundMessage = exchange.getIn();

        final Map<String, Object> headers = inboundMessage.getHeaders();

        final Map<String, List<String>> answer = new HashMap<>();
        for (final String headerName : headers.keySet()) {
            final String headerNameLowercase = headerName.toLowerCase(Locale.US);
            if (headerNameLowercase.startsWith("sforce") || headerNameLowercase.startsWith("x-sfdc")) {
                final Object headerValue = inboundMessage.getHeader(headerName);

                if (headerValue instanceof String) {
                    answer.put(headerName, Collections.singletonList((String) headerValue));
                } else if (headerValue instanceof String[]) {
                    answer.put(headerName, Arrays.asList((String[]) headerValue));
                } else if (headerValue instanceof Collection) {
                    answer.put(headerName,
                            ((Collection<?>) headerValue).stream().map(String::valueOf).collect(Collectors.<String> toList()));
                } else {
                    throw new IllegalArgumentException(
                            "Given value for header `" + headerName + "`, is not String, String array or a Collection");
                }
            }
        }
        return answer;
    }
}
