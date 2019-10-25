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
package org.apache.camel.component.graphql.server;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

public class GraphqlServer {

    private final GraphQL graphql;
    private final HttpServer server;

    public GraphqlServer() {
        this.graphql = GraphqlFactory.newGraphQL();
        this.server = ServerBootstrap.bootstrap()
                .registerHandler("/graphql", new GraphqlHandler())
                .create();
    }

    public void start() throws IOException {
        server.start();
    }

    public void shutdown() {
        server.shutdown(0, TimeUnit.SECONDS);
    }

    public int getPort() {
        return server.getLocalPort();
    }

    class GraphqlHandler implements HttpRequestHandler {

        private final ObjectMapper objectMapper = new ObjectMapper();

        public void handle(HttpRequest request, HttpResponse response, HttpContext context)
                throws HttpException, IOException {
            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                String json = EntityUtils.toString(entity);

                Map<String, Object> map = jsonToMap(json);
                String query = (String) map.get("query");
                String operationName = (String) map.get("operationName");
                Map<String, Object> variables = (Map<String, Object>) map.get("variables");

                ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                        .query(query)
                        .operationName(operationName)
                        .variables(variables)
                        .build();
                ExecutionResult executionResult = graphql.execute(executionInput);
                Map<String, Object> resultMap = executionResult.toSpecification();
                String result = objectMapper.writeValueAsString(resultMap);

                response.setHeader("Content-Type", "application/json; charset=UTF-8");
                response.setEntity(new StringEntity(result));
            }
        }

        private Map<String, Object> jsonToMap(String json) throws IOException {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        }

    }
}
