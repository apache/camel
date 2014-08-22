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
package org.apache.camel.component.resteasy;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ResteasyProducer binds a Camel exchange to a Http Request, acts as a Resteasy
 * client, and sends the request to a server.  Any response will
 * be bound to Camel exchange.
 *
 */
public class ResteasyProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ResteasyProducer.class);
    ResteasyEndpoint endpoint;

    public ResteasyProducer(ResteasyEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        ResteasyEndpoint endpoint = (ResteasyEndpoint) getEndpoint();

        LOG.debug("Uri pattern from endpoint: {}", endpoint.getUriPattern());
        String resourceUri = buildUri(endpoint, exchange);

        LOG.debug("Final URI: {} ", resourceUri);

        // setting headerFilterStrategy from endpoint to httpBinding maybe TODO upgrade
        endpoint.getRestEasyHttpBindingRef().setHeaderFilterStrategy(endpoint.getHeaderFilterStrategy());

        Map<String, String> parameters = getParameters(exchange, endpoint);

        if (endpoint.getProxyClientClass() != null) {
            // Proxy producer
            if (endpoint.getProxyClientClass().isEmpty()) {
                throw new IllegalArgumentException("Uri option proxyClientClass cannot be empty! Full class name must be specified.");
            } else {
                endpoint.getRestEasyHttpBindingRef().populateProxyResteasyRequestAndExecute(resourceUri, exchange, parameters);
            }
        } else {
            // Basic producer
            Response response = endpoint.getRestEasyHttpBindingRef().populateResteasyRequestFromExchangeAndExecute(resourceUri, exchange, parameters);
            endpoint.getRestEasyHttpBindingRef().populateExchangeFromResteasyResponse(exchange, response);

            response.close();
        }

    }


    /**
     * Building the final URI from endpoint, which will be used in the Camel-Resteasy producer for Resteasy client.
     *
     * @param endpoint Resteasy endpoint
     * @param exchange camel exchange sent to the endpoint
     * @return URI representing the final URI with query which will be used in Resteasy client
     * @throws CamelExchangeException
     */
    private static String buildUri(ResteasyEndpoint endpoint, Exchange exchange) throws CamelExchangeException {
        String uri;
        if (endpoint.getPort() == 0) {
            uri = endpoint.getProtocol() + "://" + endpoint.getHost()  + endpoint.getUriPattern();
        } else {
            uri = endpoint.getProtocol() + "://" + endpoint.getHost() + ":" + endpoint.getPort() + endpoint.getUriPattern();
        }

        String query = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        if (query != null) {
            LOG.debug("Adding query: {} to uri: {}", query, uri);
            uri = addQueryToUri(uri, query);
        }

        LOG.debug("Using uri: {}", uri);
        return uri;
    }

    /**
     * Method for adding query to URI and creating final URI for producer
     *
     * @param uri base URI
     * @param query string representing query read from HTTP_QUERY header
     * @return URI with added query
     */
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

    /**
     * Method for getting specific Camel-Resteasy options from endpoint and headers in exchange and returning the
     * correct values as Map.
     *
     * @param exchange camel exchange
     * @param endpoint endpoint on which the exchange came
     * @return map with correct values for each option relevant for Camel-Resteasy
     */
    protected static Map<String, String> getParameters(Exchange exchange, ResteasyEndpoint endpoint) {
        Map<String, String> parameters = new HashMap<String, String>();

        // Get method which should be used on producer
        String method = endpoint.getResteasyMethod();
        String methodHeader = exchange.getIn().getHeader(ResteasyConstants.RESTEASY_HTTP_METHOD, String.class);

        if (methodHeader != null && !method.equalsIgnoreCase(methodHeader)) {
            method = methodHeader;
        }

        parameters.put("method", method);

        // Get parameters for proxy producer
        String proxyClassName = endpoint.getProxyClientClass();
        parameters.put("proxyClassName", proxyClassName);

        String proxyMethodName = endpoint.getProxyMethod();
        String proxyMethodNameHeader = exchange.getIn().getHeader(ResteasyConstants.RESTEASY_PROXY_METHOD, String.class);
        if (proxyMethodNameHeader != null && !proxyMethodName.equalsIgnoreCase(proxyMethodNameHeader)) {
            proxyMethodName = proxyMethodNameHeader;
        }
        parameters.put("proxyMethodName", proxyMethodName);

        // Get parameters for basic authentication
        String usernameHeader = exchange.getIn().getHeader(ResteasyConstants.RESTEASY_USERNAME, String.class);
        String passwordHeader = exchange.getIn().getHeader(ResteasyConstants.RESTEASY_PASSWORD, String.class);
        String username = endpoint.getUsername();
        String password = endpoint.getPassword();

        if (usernameHeader != null && !username.equals(usernameHeader)) {
            username = usernameHeader;
        }
        if (passwordHeader != null && !password.equals(passwordHeader)) {
            password = passwordHeader;
        }
        parameters.put("username", username);
        parameters.put("password", password);

        return parameters;
    }

}
