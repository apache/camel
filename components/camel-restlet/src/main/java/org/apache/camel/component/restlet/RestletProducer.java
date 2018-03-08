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

import java.io.IOException;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.URISupport;
import org.restlet.Client;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Uniform;
import org.restlet.data.Cookie;
import org.restlet.data.CookieSetting;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel producer that acts as a client to Restlet server.
 *
 * @version 
 */
public class RestletProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(RestletProducer.class);
    private static final Pattern PATTERN = Pattern.compile("\\{([\\w\\.]*)\\}");
    private Client client;
    private boolean throwException;

    public RestletProducer(RestletEndpoint endpoint) throws Exception {
        super(endpoint);
        this.throwException = endpoint.isThrowExceptionOnFailure();
        client = new Client(endpoint.getProtocol());
        client.setContext(new Context());
        client.getContext().getParameters().add("socketTimeout", String.valueOf(endpoint.getSocketTimeout()));
        client.getContext().getParameters().add("socketConnectTimeoutMs", String.valueOf(endpoint.getSocketTimeout()));

        RestletComponent component = (RestletComponent) endpoint.getComponent();
        if (component.getMaxConnectionsPerHost() != null && component.getMaxConnectionsPerHost() > 0) {
            client.getContext().getParameters().add("maxConnectionsPerHost", String.valueOf(component.getMaxConnectionsPerHost()));
        }
        if (component.getMaxTotalConnections() != null && component.getMaxTotalConnections() > 0) {
            client.getContext().getParameters().add("maxTotalConnections", String.valueOf(component.getMaxTotalConnections()));
        }
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
        URI uri = new URI(resourceUri);
        request = new Request(endpoint.getRestletMethod(), resourceUri);
        binding.populateRestletRequestFromExchange(request, exchange);
        loadCookies(exchange, uri, request);

        LOG.debug("Sending request synchronously: {} for exchangeId: {}", request, exchange.getExchangeId());
        Response response = client.handle(request);
        LOG.debug("Received response synchronously: {} for exchangeId: {}", response, exchange.getExchangeId());
        if (response != null) {
            Integer respCode = response.getStatus().getCode();
            storeCookies(exchange, uri, response);
            if (respCode > 207 && throwException) {
                exchange.setException(populateRestletProducerException(exchange, response, respCode));
            } else {
                binding.populateExchangeFromRestletResponse(exchange, response);
            }
        }
    }

    private void storeCookies(Exchange exchange, URI uri, Response response) {
        RestletEndpoint endpoint = (RestletEndpoint) getEndpoint();
        if (endpoint.getCookieHandler() != null) {
            Series<CookieSetting> cookieSettings = response.getCookieSettings();
            CookieStore cookieJar = endpoint.getCookieHandler().getCookieStore(exchange);
            for (CookieSetting s:cookieSettings) {
                HttpCookie cookie = new HttpCookie(s.getName(), s.getValue());
                cookie.setComment(s.getComment());
                cookie.setDomain(s.getDomain());
                cookie.setMaxAge(s.getMaxAge());
                cookie.setPath(s.getPath());
                cookie.setSecure(s.isSecure());
                cookie.setVersion(s.getVersion());
                cookieJar.add(uri, cookie);
            }
        }
    }

    private void loadCookies(Exchange exchange, URI uri, Request request) throws IOException {
        RestletEndpoint endpoint = (RestletEndpoint) getEndpoint();
        if (endpoint.getCookieHandler() != null) {
            Series<Cookie> cookies = request.getCookies();
            Map<String, List<String>> cookieHeaders = endpoint.getCookieHandler().loadCookies(exchange, uri);
            // parse the cookies
            for (String cookieHeader : cookieHeaders.keySet()) {
                for (String cookieStr : cookieHeaders.get(cookieHeader)) {
                    for (HttpCookie cookie : HttpCookie.parse(cookieStr)) {
                        cookies.add(new Cookie(cookie.getVersion(), cookie.getName(), cookie.getValue(), cookie.getPath(), cookie.getDomain()));
                    }
                }
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
            } catch (Throwable e) {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }

        LOG.trace("Processing asynchronously");

        final RestletBinding binding = endpoint.getRestletBinding();
        Request request;
        try {
            String resourceUri = buildUri(endpoint, exchange);
            URI uri = new URI(resourceUri);
            request = new Request(endpoint.getRestletMethod(), resourceUri);
            binding.populateRestletRequestFromExchange(request, exchange);
            loadCookies(exchange, uri, request);
        } catch (Throwable e) {
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
                        String resourceUri = buildUri(endpoint, exchange);
                        URI uri = new URI(resourceUri);
                        Integer respCode = response.getStatus().getCode();
                        storeCookies(exchange, uri, response);
                        if (respCode > 207 && throwException) {
                            exchange.setException(populateRestletProducerException(exchange, response, respCode));
                        } else {
                            binding.populateExchangeFromRestletResponse(exchange, response);
                        }
                    }
                } catch (Throwable e) {
                    exchange.setException(e);
                } finally {
                    callback.done(false);
                }
            }
        });

        // we continue routing async
        return false;
    }

    private static String buildUri(RestletEndpoint endpoint, Exchange exchange) throws Exception {
        // rest producer may provide an override url to be used which we should discard if using (hence the remove)
        String uri = (String) exchange.getIn().removeHeader(Exchange.REST_HTTP_URI);

        if (uri == null) {
            uri = endpoint.getProtocol() + "://" + endpoint.getHost() + ":" + endpoint.getPort() + endpoint.getUriPattern();
        }

        // substitute { } placeholders in uri and use mandatory headers
        LOG.trace("Substituting '{value}' placeholders in uri: {}", uri);
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
        
        // include any query parameters if needed
        if (endpoint.getQueryParameters() != null) {
            uri = URISupport.appendParametersToURI(uri, endpoint.getQueryParameters());
        }

        // rest producer may provide an override query string to be used which we should discard if using (hence the remove)
        String query = (String) exchange.getIn().removeHeader(Exchange.REST_HTTP_QUERY);
        if (query == null) {
            query = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        }
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
