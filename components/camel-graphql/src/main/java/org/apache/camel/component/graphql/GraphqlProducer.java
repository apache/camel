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

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.http.HttpConstants;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.hc.client5.http.classic.HttpClient;

public class GraphqlProducer extends DefaultAsyncProducer {

    private static final String ACCEPT = "Accept";
    private static final String ACCEPT_ENCODING = "Accept-Encoding";

    private Producer http;

    public GraphqlProducer(GraphqlEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        Registry registry = getEndpoint().getCamelContext().getRegistry();
        String hash = Integer.toHexString(getEndpoint().getEndpointUri().hashCode());

        String serviceUrl = getEndpoint().getServiceUrl();
        StringBuilder sb = new StringBuilder(serviceUrl);
        // httpUri has a query of its own when the endpoint has lenient parameters (such as an api key)
        sb.append(serviceUrl.indexOf('?') < 0 ? '?' : '&').append("httpMethod=POST");
        sb.append("&throwExceptionOnFailure=").append(getEndpoint().isThrowExceptionOnFailure());
        // the request always goes to httpUri: CamelHttpUri, CamelHttpPath and the other camel-http control headers
        // of the message must not change it
        sb.append("&skipControlHeaders=true");

        String clientConfigurerName = "graphqlHttpClientConfigurer-" + hash;
        registry.bind(clientConfigurerName, getEndpoint().createHttpClientConfigurer());
        sb.append("&httpClientConfigurer=#").append(clientConfigurerName);

        HeaderFilterStrategy headerFilterStrategy = getEndpoint().getHeaderFilterStrategy();
        if (headerFilterStrategy != null) {
            String filterStrategyName = "graphqlHeaderFilterStrategy-" + hash;
            registry.bind(filterStrategyName, headerFilterStrategy);
            sb.append("&headerFilterStrategy=#").append(filterStrategyName);
        }

        HttpClient httpClient = getEndpoint().getHttpClient();
        if (httpClient != null) {
            String httpClientName = "graphqlHttpClient-" + hash;
            registry.bind(httpClientName, httpClient);
            sb.append("&httpClient=#").append(httpClientName);
        }

        Endpoint httpEndpoint = getEndpoint().getCamelContext().getEndpoint(sb.toString());
        http = httpEndpoint.createProducer();
        ServiceHelper.startService(http);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(http);
    }

    @Override
    public GraphqlEndpoint getEndpoint() {
        return (GraphqlEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {

        Exchange httpExchange = getEndpoint().createExchange();
        Message request = httpExchange.getIn();
        MessageHelper.copyHeaders(exchange.getMessage(), request, true);
        // the query and the variables are sent in the JSON body, not as HTTP headers
        if (getEndpoint().getQueryHeader() != null) {
            request.removeHeader(getEndpoint().getQueryHeader());
        }
        if (getEndpoint().getVariablesHeader() != null) {
            request.removeHeader(getEndpoint().getVariablesHeader());
        }
        // camel-http would compress the JSON body for a Content-Encoding header of the message
        request.removeHeader(Exchange.CONTENT_ENCODING);
        // the headers of the producer win over the headers of the message
        // not read by camel-http (skipControlHeaders=true): the httpMethod option of the internal endpoint decides
        request.setHeader(HttpConstants.HTTP_METHOD, "post");
        request.setHeader(Exchange.CONTENT_TYPE, "application/json");
        request.setHeader(ACCEPT, "application/json");
        request.setHeader(ACCEPT_ENCODING, "gzip");

        try {
            String requestBody
                    = buildRequestBody(getQuery(exchange), getEndpoint().getOperationName(), getVariables(exchange));
            request.setBody(requestBody);
            try {
                http.process(httpExchange);
                String data = httpExchange.getMessage().getBody(String.class);
                exchange.getIn().setBody(data);
            } catch (Exception e) {
                exchange.setException(e);
            }
            Message response = httpExchange.getMessage();
            // the headers the producer set for the request do not stay on the message (a header of the message
            // with the same name keeps its value); the Content-Type of the response of the server is kept
            response.removeHeader(HttpConstants.HTTP_METHOD);
            response.removeHeader(ACCEPT);
            response.removeHeader(ACCEPT_ENCODING);
            if (response == request) {
                // no response message (a failed call): the Content-Type is the one of the request
                response.removeHeader(Exchange.CONTENT_TYPE);
            }
            MessageHelper.copyHeaders(response, exchange.getIn(), true);
        } catch (Exception e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
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
            Object body = exchange.getIn().getBody();
            if (body != null && !isQueryBody(body)) {
                throw new InvalidPayloadException(
                        exchange, String.class, exchange.getIn(),
                        "the body is the GraphQL query when the query, queryFile and queryHeader options are not set,"
                                                                  + " and must be text, bytes, a stream or a file");
            }
            query = exchange.getIn().getMandatoryBody(String.class);
        }
        return query;
    }

    private static boolean isQueryBody(Object body) {
        return body instanceof CharSequence || body instanceof byte[] || body instanceof InputStream
                || body instanceof Reader || body instanceof File || body instanceof Path
                || body instanceof WrappedFile;
    }

    private JsonObject getVariables(Exchange exchange) {
        JsonObject variables = null;
        if (getEndpoint().getVariables() != null) {
            variables = getEndpoint().getVariables();
        } else if (getEndpoint().getVariablesHeader() != null) {
            Object value = exchange.getIn().getHeader(getEndpoint().getVariablesHeader());
            if (value instanceof Map<?, ?> map) {
                variables = toJsonObject(map);
            } else {
                variables = exchange.getIn().getHeader(getEndpoint().getVariablesHeader(), JsonObject.class);
            }
        } else if (exchange.getIn().getBody() instanceof Map<?, ?> map) {
            variables = toJsonObject(map);
        }
        return variables;
    }

    private static JsonObject toJsonObject(Map<?, ?> map) {
        if (map instanceof JsonObject json) {
            return json;
        }
        // the variable names of GraphQL are text, so a key that is not a String is turned into one
        JsonObject json = new JsonObject();
        map.forEach((key, value) -> json.put(String.valueOf(key), value));
        return json;
    }
}
