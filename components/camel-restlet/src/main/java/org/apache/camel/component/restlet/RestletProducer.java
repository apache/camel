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
package org.apache.camel.component.restlet;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.restlet.Client;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Uniform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel producer that acts as a client to Restlet server.
 *
 * @version 
 */
public class RestletProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(RestletProducer.class);
    private static final Pattern PATTERN = Pattern.compile("\\(([\\w\\.]*)\\)");
    private Client client;
    private boolean throwException;

    public RestletProducer(RestletEndpoint endpoint) throws Exception {
        super(endpoint);
        this.throwException = endpoint.isThrowExceptionOnFailure();
        client = new Client(endpoint.getProtocol());
        client.setContext(new Context());
        client.getContext().getParameters().add("socketTimeout", String.valueOf(endpoint.getSocketTimeout()));
        client.getContext().getParameters().add("socketConnectTimeoutMs", String.valueOf(endpoint.getSocketTimeout()));
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        client.start();
    }

    @Override
    public void doStop() throws Exception {
        client.stop();
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        RestletEndpoint endpoint = (RestletEndpoint) getEndpoint();

        final RestletBinding binding = endpoint.getRestletBinding();
        Request request;
        String resourceUri = buildUri(endpoint, exchange);
        request = new Request(endpoint.getRestletMethod(), resourceUri);
        binding.populateRestletRequestFromExchange(request, exchange);

        LOG.debug("Sending request synchronously: {} for exchangeId: {}", request, exchange.getExchangeId());
        Response response = client.handle(request);
        LOG.debug("Received response synchronously: {} for exchangeId: {}", response, exchange.getExchangeId());
        if (response != null) {
            Integer respCode = response.getStatus().getCode();
            if (respCode > 207 && throwException) {
                exchange.setException(populateRestletProducerException(exchange, response, respCode));
            } else {
                binding.populateExchangeFromRestletResponse(exchange, response);
            }
        }
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        RestletEndpoint endpoint = (RestletEndpoint) getEndpoint();

        // force processing synchronously using different api
        if (endpoint.isSynchronous()) {
            try {
                process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
            return true;
        }

        LOG.trace("Processing asynchronously");

        final RestletBinding binding = endpoint.getRestletBinding();
        Request request;
        try {
            String resourceUri = buildUri(endpoint, exchange);
            request = new Request(endpoint.getRestletMethod(), resourceUri);
            binding.populateRestletRequestFromExchange(request, exchange);
        } catch (CamelExchangeException e) {
            // break out in case of exception
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // process the request asynchronously
        LOG.debug("Sending request asynchronously: {} for exchangeId: {}", request, exchange.getExchangeId());
        client.handle(request, new Uniform() {
            @Override
            public void handle(Request request, Response response) {
                LOG.debug("Received response asynchronously: {} for exchangeId: {}", response, exchange.getExchangeId());
                try {
                    if (response != null) {
                        Integer respCode = response.getStatus().getCode();
                        if (respCode > 207 && throwException) {
                            exchange.setException(populateRestletProducerException(exchange, response, respCode));
                        } else {
                            binding.populateExchangeFromRestletResponse(exchange, response);
                        }
                    }
                } catch (Exception e) {
                    exchange.setException(e);
                } finally {
                    callback.done(false);
                }
            }
        });

        // we continue routing async
        return false;
    }

    private static String buildUri(RestletEndpoint endpoint, Exchange exchange) throws CamelExchangeException {
        String uri = endpoint.getProtocol() + "://" + endpoint.getHost() + ":" + endpoint.getPort() + endpoint.getUriPattern();

        // substitute { } placeholders in uri and use mandatory headers
        LOG.trace("Substituting '(value)' placeholders in uri: {}", uri);
        Matcher matcher = PATTERN.matcher(uri);
        while (matcher.find()) {
            String key = matcher.group(1);
            String header = exchange.getIn().getHeader(key, String.class);
            // header should be mandatory
            if (header == null) {
                throw new CamelExchangeException("Header with key: " + key + " not found in Exchange", exchange);
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Replacing: {} with header value: {}", matcher.group(0), header);
            }

            uri = matcher.replaceFirst(header);
            // we replaced uri so reset and go again
            matcher.reset(uri);
        }

        String query = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        if (query != null) {
            LOG.trace("Adding query: {} to uri: {}", query, uri);
            uri = addQueryToUri(uri, query);
        }

        LOG.trace("Using uri: {}", uri);
        return uri;
    }

    protected static String addQueryToUri(String uri, String query) {
        if (uri == null || uri.length() == 0) {
            return uri;
        }

        StringBuilder answer = new StringBuilder();

        int index = uri.indexOf('?');
        if (index < 0) {
            answer.append(uri);
            answer.append("?");
            answer.append(query);
        } else {
            answer.append(uri.substring(0, index));
            answer.append("?");
            answer.append(query);
            String remaining = uri.substring(index + 1);
            if (remaining.length() > 0) {
                answer.append("&");
                answer.append(remaining);
            }
        }
        return answer.toString();

    }

    protected RestletOperationException populateRestletProducerException(Exchange exchange, Response response, int responseCode) {
        RestletOperationException exception;
        String uri = response.getRequest().getResourceRef().toString();
        String statusText = response.getStatus().getDescription();
        Map<String, String> headers = parseResponseHeaders(response, exchange);
        String copy;
        if (response.getEntity() != null) {
            try {
                copy = response.getEntity().getText();
            } catch (Exception ex) {
                copy = ex.toString();
            }
        } else {
            copy = response.toString();
        }
        if (responseCode >= 300 && responseCode < 400) {
            String redirectLocation;
            if (response.getStatus().isRedirection()) {
                redirectLocation = response.getLocationRef().getHostIdentifier();
                exception = new RestletOperationException(uri, responseCode, statusText, redirectLocation, headers, copy);
            } else {
                //no redirect location
                exception = new RestletOperationException(uri, responseCode, statusText, null, headers, copy);
            }
        } else {
            //internal server error(error code 500)
            exception = new RestletOperationException(uri, responseCode, statusText, null, headers, copy);
        }

        return exception;
    }

    protected Map<String, String> parseResponseHeaders(Object response, Exchange camelExchange) {

        Map<String, String> answer = new HashMap<String, String>();
        if (response instanceof Response) {

            for (Map.Entry<String, Object> entry : ((Response) response).getAttributes().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                LOG.trace("Parse external header {}={}", key, value);
                answer.put(key, value.toString());
            }
        }

        return answer;
    }
}
