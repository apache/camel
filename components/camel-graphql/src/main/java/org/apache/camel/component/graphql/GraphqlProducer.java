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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Producer;
import org.apache.camel.component.http.HttpConstants;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.hc.client5.http.classic.HttpClient;

public class GraphqlProducer extends DefaultAsyncProducer {

    private Producer http;

    public GraphqlProducer(GraphqlEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        Registry registry = getEndpoint().getCamelContext().getRegistry();
        String hash = Integer.toHexString(getEndpoint().getEndpointUri().hashCode());

        StringBuilder sb = new StringBuilder(getEndpoint().getServiceUrl());
        sb.append("?httpMethod=POST");
        sb.append("&throwExceptionOnFailure=").append(getEndpoint().isThrowExceptionOnFailure());

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
        httpExchange.getIn().setHeader(HttpConstants.HTTP_METHOD, "post");
        httpExchange.getIn().setHeader("Content-Type", "application/json");
        httpExchange.getIn().setHeader("Accept", "application/json");
        httpExchange.getIn().setHeader("Accept-Encoding", "gzip");
        MessageHelper.copyHeaders(exchange.getMessage(), httpExchange.getIn(), true);

        try {
            String requestBody
                    = buildRequestBody(getQuery(exchange), getEndpoint().getOperationName(), getVariables(exchange));
            httpExchange.getIn().setBody(requestBody);
            try {
                http.process(httpExchange);
                String data = httpExchange.getMessage().getBody(String.class);
                exchange.getIn().setBody(data);
            } catch (Exception e) {
                exchange.setException(e);
            }
            MessageHelper.copyHeaders(httpExchange.getMessage(), exchange.getIn(), true);
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
