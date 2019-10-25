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
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.json.JsonObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

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
            String requestBody = buildRequestBody(getEndpoint().getQuery(), getEndpoint().getOperationName(),
                    getEndpoint().getVariables());
            HttpEntity requestEntity = new StringEntity(requestBody, ContentType.create("application/json", "UTF-8"));

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
}
