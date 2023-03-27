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
import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.graphql.server.GraphqlServer;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GraphqlComponentTest extends CamelTestSupport {

    private static String booksQueryResult;
    private static String bookByIdQueryResult;
    private static String addBookMutationResult;
    private static GraphqlServer server;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @BeforeAll
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

    @AfterAll
    public static void tearDownAfterClass() {
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
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start1")
                        .to("graphql://http://localhost:" + server.getPort() + "/graphql?query={books{id name}}")
                        .to("mock:result");
                from("direct:start2")
                        .to("graphql://http://localhost:" + server.getPort() + "/graphql?queryFile=booksQuery.graphql")
                        .to("mock:result");
                from("direct:start3")
                        .to("graphql://http://localhost:" + server.getPort()
                            + "/graphql?queryFile=multipleQueries.graphql&operationName=BookById&variables=#bookByIdQueryVariables")
                        .to("mock:result");
                from("direct:start4")
                        .to("graphql://http://localhost:" + server.getPort()
                            + "/graphql?queryFile=addBookMutation.graphql&variables=#addBookMutationVariables")
                        .to("mock:result");
                from("direct:start5")
                        .to("graphql://http://localhost:" + server.getPort()
                            + "/graphql?query={books{id name}}&variablesHeader=bookByIdQueryVariables")
                        .to("mock:result");
                from("direct:start6")
                        .to("graphql://http://localhost:" + server.getPort()
                            + "/graphql")
                        .to("mock:result");
                from("direct:start7")
                        .setHeader("myQuery", constant("{books{id name}}"))
                        .to("graphql://http://localhost:" + server.getPort()
                            + "/graphql?queryHeader=myQuery")
                        .to("mock:result");
                from("direct:start8")
                        .to("graphql://http://localhost:" + server.getPort() + "/graphql?apikey=123456&query={books{id name}}")
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
    public void booksQueryWithStaticQueryInBody() throws Exception {
        result.expectedMessageCount(1);
        result.expectedBodiesReceived(booksQueryResult);

        template.sendBody("direct:start6", "{books{id name}}");

        result.assertIsSatisfied();
    }

    @Test
    public void booksQueryWithStaticQueryInHeader() throws Exception {
        result.expectedMessageCount(1);
        result.expectedBodiesReceived(booksQueryResult);

        template.sendBody("direct:start7", "");

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

    @Test
    public void booksQueryWithVariablesHeader() throws Exception {
        result.expectedMessageCount(1);
        result.expectedBodiesReceived(booksQueryResult);

        JsonObject variables = new JsonObject();
        variables.put("id", "book-1");
        template.sendBodyAndHeader("direct:start5", "", "bookByIdQueryVariables", variables);

        result.assertIsSatisfied();
    }

    @Test
    public void booksQueryWithVariablesBody() throws Exception {
        result.expectedMessageCount(1);
        result.expectedBodiesReceived(booksQueryResult);

        JsonObject variables = new JsonObject();
        variables.put("id", "book-1");
        template.sendBody("direct:start1", variables);

        result.assertIsSatisfied();
    }

    @Test
    public void checkApiKey() throws Exception {

        GraphqlEndpoint graphqlEndpoint = (GraphqlEndpoint) template.getCamelContext().getEndpoint(
                "graphql://http://localhost:" + server.getPort() + "/graphql?apikey=123456&query={books{id name}}");
        URI httpUri = graphqlEndpoint.getHttpUri();
        assertEquals("apikey=123456", httpUri.getQuery());

        result.expectedMessageCount(1);
        result.expectedBodiesReceived(booksQueryResult);

        template.sendBody("direct:start8", "");

        result.assertIsSatisfied();

    }
}
