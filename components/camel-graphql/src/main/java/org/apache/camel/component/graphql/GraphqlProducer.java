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

import java.net.URI;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.json.JsonObject;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class GraphqlProducer extends DefaultAsyncProducer {

    public GraphqlProducer(GraphqlEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public GraphqlEndpoint getEndpoint() {
        return (GraphqlEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            CloseableHttpClient httpClient = getEndpoint().getHttpclient();
            URI httpUri = getEndpoint().getHttpUri();
            String requestBody = buildRequestBody(getQuery(exchange), getEndpoint().getOperationName(),
                    getVariables(exchange));
            HttpEntity requestEntity = new StringEntity(requestBody, ContentType.APPLICATION_JSON);

            HttpPost httpPost = new HttpPost(httpUri);
            httpPost.setHeader(HttpHeaders.ACCEPT, "application/json");
            httpPost.setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip");
            httpPost.setEntity(requestEntity);

            String responseContent = httpClient.execute(httpPost, response -> EntityUtils.toString(response.getEntity()));

            exchange.getMessage().setBody(responseContent);

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
