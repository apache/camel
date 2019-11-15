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

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.graphql.server.GraphqlServer;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class GraphqlComponentTest extends CamelTestSupport {

    private static String booksQueryResult;
    private static String bookByIdQueryResult;
    private static String addBookMutationResult;
    private static GraphqlServer server;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        booksQueryResult = readJsonFile("booksQueryResult.json");
        bookByIdQueryResult = readJsonFile("bookByIdQueryResult.json");
        addBookMutationResult = readJsonFile("addBookMutationResult.json");

        server = new GraphqlServer();
        server.start();
    }

    private static String readJsonFile(String name) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(objectMapper.readValue(
            IOHelper.loadText(ObjectHelper.loadResourceAsStream(name)), Object.class));
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        server.shutdown();
    }

    @BindToRegistry("bookByIdQueryVariables")
    public JsonObject bookByIdQueryVariables() {
        JsonObject variables = new JsonObject();
        variables.put("id", "book-1");
        return variables;
    }

    @BindToRegistry("addBookMutationVariables")
    public JsonObject addBookMutationVariables() {
        JsonObject bookInput = new JsonObject();
        bookInput.put("name", "Typee");
        bookInput.put("authorId", "author-2");
        JsonObject variables = new JsonObject();
        variables.put("bookInput", bookInput);
        return variables;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start1")
                    .to("graphql://http://localhost:" + server.getPort() + "/graphql?query={books{id name}}")
                    .to("mock:result");
                from("direct:start2")
                    .to("graphql://http://localhost:" + server.getPort() + "/graphql?queryFile=booksQuery.graphql")
                    .to("mock:result");
                from("direct:start3")
                    .to("graphql://http://localhost:" + server.getPort() + "/graphql?queryFile=multipleQueries.graphql&operationName=BookById&variables=#bookByIdQueryVariables")
                    .to("mock:result");
                from("direct:start4")
                    .to("graphql://http://localhost:" + server.getPort() + "/graphql?queryFile=addBookMutation.graphql&variables=#addBookMutationVariables")
                    .to("mock:result");
            }
        };
    }

    @Test
    public void booksQueryWithStaticQuery() throws Exception {
        result.expectedMessageCount(1);
        result.expectedBodiesReceived(booksQueryResult);

        template.sendBody("direct:start1", "");

        result.assertIsSatisfied();
    }

    @Test
    public void booksQueryWithQueryFile() throws Exception {
        result.expectedMessageCount(1);
        result.expectedBodiesReceived(booksQueryResult);

        template.sendBody("direct:start2", "");

        result.assertIsSatisfied();
    }

    @Test
    public void bookByIdQuery() throws Exception {
        result.expectedMessageCount(1);
        result.expectedBodiesReceived(bookByIdQueryResult);

        template.sendBody("direct:start3", "");

        result.assertIsSatisfied();
    }

    @Test
    public void addBookMutation() throws Exception {
        result.expectedMessageCount(1);
        result.expectedBodiesReceived(addBookMutationResult);

        template.sendBody("direct:start4", "");

        result.assertIsSatisfied();
    }

}
