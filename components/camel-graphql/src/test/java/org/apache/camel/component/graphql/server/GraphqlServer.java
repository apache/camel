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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;

public class GraphqlServer {

    private final GraphQL graphql;
    private final HttpServer server;

    public GraphqlServer() {
        this.graphql = GraphqlFactory.newGraphQL();
        this.server = ServerBootstrap.bootstrap()
                .register("/graphql", new GraphqlHandler())
                .create();
    }

    public void start() throws IOException {
        server.start();
    }

    public void shutdown() {
        server.close();
    }

    public int getPort() {
        return server.getLocalPort();
    }

    class GraphqlHandler implements HttpRequestHandler {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @SuppressWarnings("unchecked")
        public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
                throws HttpException, IOException {
            HttpEntity entity = request.getEntity();
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

        private Map<String, Object> jsonToMap(String json) throws IOException {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        }

    }
}
