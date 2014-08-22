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

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.BasicAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default Resteasy binding implementation
 */
public class DefaultResteasyHttpBinding implements ResteasyHttpBinding {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultResteasyHttpBinding.class);

    private HeaderFilterStrategy headerFilterStrategy;

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    @Override
    public Response populateResteasyRequestFromExchangeAndExecute(String uri, Exchange exchange, Map<String, String> parameters) {
        Client client = ClientBuilder.newBuilder().build();
        String body = exchange.getIn().getBody(String.class);

        LOG.debug("Body in producer: {}", body);

        String mediaType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);

        WebTarget target = client.target(uri);

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
            if (headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToCamelHeaders(key, value, exchange)) {
                builder.header(key, value);
                LOG.debug("Populate Resteasy request from exchange header: {} value: {}", key, value);
            }
        }

        if (parameters.get("basicAuth") != null && Boolean.TRUE.equals(ObjectHelper.cast(Boolean.class, parameters.get("basicAuth")))) {
            if (parameters.get("username") != null && parameters.get("password") != null) {
                target.register(new BasicAuthentication(parameters.get("username"), parameters.get("password")));
                LOG.debug("Basic authentication was applied");
            }
        }
        
        String method = parameters.get("method");

        if (method.equals("GET")) {
            return builder.get();
        }
        if (method.equals("POST")) {
            return  builder.post(Entity.entity(body, mediaType));
        }
        if (method.equals("PUT")) {
            return  builder.put(Entity.entity(body, mediaType));
        }
        if (method.equals("DELETE")) {
            return  builder.delete();
        }
        if (method.equals("OPTIONS")) {
            return  builder.options();
        }
        if (method.equals("TRACE")) {
            return  builder.trace();
        }
        if (method.equals("HEAD")) {
            return  builder.head();
        }

        // maybe throw exception because not method was correct
        throw new IllegalArgumentException("Method '" + method + "' is not supported method");
    }


    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void populateProxyResteasyRequestAndExecute(String uri, Exchange exchange, Map<String, String> parameters) {
        Client client = ClientBuilder.newBuilder().build();

        WebTarget target = client.target(uri);

        if (parameters.get("basicAuth") != null && Boolean.TRUE.equals(ObjectHelper.cast(Boolean.class, parameters.get("basicAuth")))) {
            if (parameters.get("username") != null && parameters.get("password") != null) {
                target.register(new BasicAuthentication(parameters.get("username"), parameters.get("password")));
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Basic authentication was applied");
                }
            }
        }

        Class realClazz;
        Object object = null;
        try {
            realClazz = Class.forName(parameters.get("proxyClassName"));
            Object simple = ObjectHelper.cast(ResteasyWebTarget.class, target).proxy(realClazz);
            
            ArrayList headerParams = exchange.getIn().getHeader(ResteasyConstants.RESTEASY_PROXY_METHOD_PARAMS, ArrayList.class);

            if (headerParams != null) {
                Object[] args = new Object[headerParams.size()];
                Class[] paramsClasses = new Class[headerParams.size()];
                for (int i = 0; i < headerParams.size(); i++) {
                    paramsClasses[i] = headerParams.get(i).getClass();
                    args[i] = headerParams.get(i);
                }

                Method m = simple.getClass().getMethod(parameters.get("proxyMethodName"), paramsClasses);
                object = m.invoke(simple, args);
            } else {
                Method m = simple.getClass().getMethod(parameters.get("proxyMethodName"));
                object = m.invoke(simple);
            }

            if (object instanceof Response) {
                // using proxy client with return type response, creates some problem with readEntity and response needs to be
                // closed manually for correct return type to user.
                populateExchangeFromResteasyResponse(exchange, (Response) object);
                ((Response) object).close();
            } else {
                exchange.getMessage().setBody(object);
                // preserve headers from in by copying any non existing headers
                // to avoid overriding existing headers with old values
                MessageHelper.copyHeaders(exchange.getIn(), exchange.getMessage(), false);
            }


        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            exchange.getMessage().getHeaders().put(ResteasyConstants.RESTEASY_PROXY_PRODUCER_EXCEPTION, e);
            exchange.getMessage().setBody(e);
            LOG.error("Camel RESTEasy proxy exception: {}", e);
        }
    }

    @Override
    public void populateExchangeFromResteasyResponse(Exchange exchange, Response response) {
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

        LOG.debug("Headers from exchange.getIn() : {}", exchange.getIn().getHeaders().toString());
        LOG.debug("Headers from exchange.getOut() before copying : {}", exchange.getMessage().getHeaders().toString());
        LOG.debug("Header from response : {}", response.getHeaders().toString());

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
