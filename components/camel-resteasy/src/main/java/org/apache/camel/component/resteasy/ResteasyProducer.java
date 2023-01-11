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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.BasicAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ResteasyProducer binds a Camel exchange to a Http Request, acts as a Resteasy client, and sends the request to a
 * server. Any response will be bound to Camel exchange.
 */
public class ResteasyProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ResteasyProducer.class);
    private static final String RESTEASY_METHOD_OPTION = "method";
    private static final String RESTEASY_PROXY_METHOD_NAME_OPTION = "proxyMethodName";
    private static final String RESTEASY_USERNAME_OPTION = "username";
    private static final String RESTEASY_PASSWORD_OPTION = "password";

    public ResteasyProducer(ResteasyEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        ResteasyEndpoint resteasyEndpoint = ObjectHelper.cast(ResteasyEndpoint.class, getEndpoint());
        Map<String, String> parameters = getParameters(exchange, resteasyEndpoint);

        String query = exchange.getIn().getHeader(ResteasyConstants.HTTP_QUERY, String.class);
        String uri = resteasyEndpoint.buildUri();
        if (query != null) {
            LOG.debug("Adding query: {} to uri: {}", query, uri);
            uri = addQueryToUri(uri, query);
            LOG.debug("URI: {} populated via ResteasyConstants.HTTP_QUERY header", uri);
        }

        WebTarget resteasyWebTarget = createWebClientTarget(uri);

        applyAuth(resteasyEndpoint, parameters, resteasyWebTarget);

        if (resteasyEndpoint.getProxyClientClass() != null) {
            // producer with resteasy proxy
            if (resteasyEndpoint.getProxyClientClass().isEmpty()) {
                throw new IllegalArgumentException(
                        "Uri option proxyClientClass cannot be empty! Full class name must be specified.");
            } else {
                writeResponseByProxy(exchange, resteasyEndpoint, parameters, resteasyWebTarget);
            }
        } else {
            writeResponse(exchange, resteasyEndpoint, parameters, resteasyWebTarget);
        }
    }

    /**
     * Method for getting specific Camel-Resteasy options from endpoint and headers in exchange and returning the
     * correct values as Map.
     *
     * @param  exchange camel exchange
     * @param  endpoint endpoint on which the exchange came
     * @return          map with correct values for each option relevant for Camel-Resteasy
     */
    protected static Map<String, String> getParameters(Exchange exchange, ResteasyEndpoint endpoint) {
        Map<String, String> parameters = new HashMap<String, String>();

        String methodHeader = exchange.getIn().getHeader(ResteasyConstants.RESTEASY_HTTP_METHOD, String.class);
        // Get method which should be used on producer
        String method = endpoint.getResteasyMethod();
        if (methodHeader != null && !method.equalsIgnoreCase(methodHeader)) {
            method = methodHeader;
        }

        parameters.put(RESTEASY_METHOD_OPTION, method);

        String proxyMethodNameHeader = exchange.getIn().getHeader(ResteasyConstants.RESTEASY_PROXY_METHOD, String.class);
        String proxyMethodName = endpoint.getProxyMethod();
        if (proxyMethodNameHeader != null && !proxyMethodName.equalsIgnoreCase(proxyMethodNameHeader)) {
            proxyMethodName = proxyMethodNameHeader;
        }

        parameters.put(RESTEASY_PROXY_METHOD_NAME_OPTION, proxyMethodName);

        // Get parameters for basic authentication
        String usernameHeader = exchange.getIn().getHeader(ResteasyConstants.RESTEASY_USERNAME, String.class);
        String username = endpoint.getUsername();
        if (usernameHeader != null && !username.equals(usernameHeader)) {
            username = usernameHeader;
        }

        parameters.put(RESTEASY_USERNAME_OPTION, username);

        String passwordHeader = exchange.getIn().getHeader(ResteasyConstants.RESTEASY_PASSWORD, String.class);
        String password = endpoint.getPassword();
        if (passwordHeader != null && !password.equals(passwordHeader)) {
            password = passwordHeader;
        }

        parameters.put(RESTEASY_PASSWORD_OPTION, password);
        return parameters;
    }

    /**
     * Method for adding query to URI and creating final URI for producer
     *
     * @param  uri   base URI
     * @param  query string representing query read from HTTP_QUERY header
     * @return       URI with added query
     */
    private String addQueryToUri(String uri, String query) {
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

    private WebTarget createWebClientTarget(String uri) {
        Client client = ClientBuilder.newBuilder().build();
        return client.target(uri);
    }

    private void applyAuth(
            ResteasyEndpoint resteasyEndpoint, Map<String, String> parameters,
            WebTarget resteasyWebTarget) {
        if (resteasyEndpoint.getBasicAuth() != null && Boolean.TRUE.equals(resteasyEndpoint.getBasicAuth())) {
            if (parameters.get(RESTEASY_USERNAME_OPTION) != null) {
                resteasyWebTarget.register(new BasicAuthentication(
                        parameters.get(RESTEASY_USERNAME_OPTION), parameters.get(RESTEASY_USERNAME_OPTION)));
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Basic authentication was applied");
                }
            }
        }
    }

    private Object createResponseObject(
            Exchange exchange, ResteasyEndpoint resteasyEndpoint,
            Map<String, String> parameters, WebTarget resteasyWebTarget) {
        Object object = null;
        try {
            Class realClazz = Class.forName(resteasyEndpoint.getProxyClientClass());
            Object simple = ObjectHelper.cast(ResteasyWebTarget.class, resteasyWebTarget).proxy(realClazz);

            ArrayList headerParams
                    = exchange.getIn().getHeader(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, ArrayList.class);

            if (headerParams != null) {
                Object[] args = new Object[headerParams.size()];
                Class[] paramsClasses = new Class[headerParams.size()];
                for (int i = 0; i < headerParams.size(); i++) {
                    paramsClasses[i] = headerParams.get(i).getClass();
                    args[i] = headerParams.get(i);
                }

                Method m = simple.getClass().getMethod(parameters.get(RESTEASY_PROXY_METHOD_NAME_OPTION), paramsClasses);
                object = m.invoke(simple, args);
            } else {
                Method m = simple.getClass().getMethod(parameters.get(RESTEASY_PROXY_METHOD_NAME_OPTION));
                object = m.invoke(simple);
            }
        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            exchange.getMessage().getHeaders().put(ResteasyConstants.RESTEASY_PROXY_PRODUCER_EXCEPTION, e);
            exchange.getMessage().setBody(e);
            LOG.error("Camel Resteasy Proxy Client Exception: {}", e.getMessage(), e);
        }
        return object;
    }

    private void writeResponseByProxy(
            Exchange exchange, ResteasyEndpoint resteasyEndpoint,
            Map<String, String> parameters, WebTarget resteasyWebTarget) {
        Object object = createResponseObject(exchange, resteasyEndpoint, parameters,
                resteasyWebTarget);

        if (object == null) {
            // maybe throw exception because not method was correct
            throw new IllegalArgumentException("Proxy Method parameters failed to create response");
        }

        if (object instanceof Response) {
            // using proxy client with return type response, creates some problem with readEntity and response needs to be
            // closed manually for correct return type to user.
            Response response = ObjectHelper.cast(Response.class, object);
            doWriteResponse(exchange, response, resteasyEndpoint.getHeaderFilterStrategy());
            response.close();
        } else {
            exchange.getMessage().setBody(object);
            // preserve headers from in by copying any non existing headers
            // to avoid overriding existing headers with old values
            MessageHelper.copyHeaders(exchange.getIn(), exchange.getMessage(), false);
        }
    }

    private Response createResponse(
            Exchange exchange, ResteasyEndpoint resteasyEndpoint, Map<String, String> parameters, WebTarget target) {
        String body = exchange.getIn().getBody(String.class);
        LOG.debug("Body in producer: {}", body);

        String mediaType = exchange.getIn().getHeader(ResteasyConstants.CONTENT_TYPE, String.class);

        LOG.debug("Populate Resteasy request from exchange body: {} using media type {}", body, mediaType);

        Invocation.Builder builder;
        if (mediaType != null) {
            builder = target.request(mediaType);
        } else {
            builder = target.request();
        }

        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (resteasyEndpoint.getHeaderFilterStrategy() != null
                    && !resteasyEndpoint.getHeaderFilterStrategy().applyFilterToCamelHeaders(key, value, exchange)) {
                builder.header(key, value);
                LOG.debug("Populate Resteasy request from exchange header: {} value: {}", key, value);
            }
        }

        String method = parameters.get(RESTEASY_METHOD_OPTION);

        Response response = null;
        if (method.equals("GET")) {
            response = builder.get();
        }
        if (method.equals("POST")) {
            response = builder.post(Entity.entity(body, mediaType));
        }
        if (method.equals("PUT")) {
            response = builder.put(Entity.entity(body, mediaType));
        }
        if (method.equals("DELETE")) {
            response = builder.delete();
        }
        if (method.equals("OPTIONS")) {
            response = builder.options();
        }
        if (method.equals("TRACE")) {
            response = builder.trace();
        }
        if (method.equals("HEAD")) {
            response = builder.head();
        }
        return response;
    }

    private void writeResponse(
            Exchange exchange, ResteasyEndpoint resteasyEndpoint, Map<String, String> parameters, WebTarget target) {
        Response response = createResponse(exchange, resteasyEndpoint, parameters, target);
        if (response == null) {
            // maybe throw exception because not method was correct
            throw new IllegalArgumentException(
                    "Method '" + parameters.get(RESTEASY_METHOD_OPTION) + "' is not supported method to create response");
        }
        doWriteResponse(exchange, response, resteasyEndpoint.getHeaderFilterStrategy());
        response.close();
        return;
    }

    public void doWriteResponse(
            Exchange exchange, Response response,
            HeaderFilterStrategy headerFilterStrategy) {
        // set response code
        int responseCode = response.getStatus();
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_RESPONSE_CODE, responseCode);

        for (String key : response.getHeaders().keySet()) {
            Object value = response.getHeaders().get(key);
            if (headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToExternalHeaders(key, value, exchange)) {
                headers.put(key, value);
                LOG.debug("Populate Camel exchange from response: {} value: {}", key, value);
            }
        }

        // set resteasy response as header so the end user has access to it if needed
        headers.put(ResteasyConstants.RESTEASY_RESPONSE, response);
        exchange.getMessage().setHeaders(headers);

        LOG.debug("Headers from exchange.getIn() : {}", exchange.getIn().getHeaders());
        LOG.debug("Headers from exchange.getOut() before copying : {}", exchange.getMessage().getHeaders());
        LOG.debug("Header from response : {}", response.getHeaders());

        if (response.hasEntity()) {
            exchange.getMessage().setBody(response.readEntity(String.class));
        } else {
            exchange.getMessage().setBody(response.getStatusInfo());
        }

        // preserve headers from in by copying any non existing headers
        // to avoid overriding existing headers with old values
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getMessage(), false);
    }
}
