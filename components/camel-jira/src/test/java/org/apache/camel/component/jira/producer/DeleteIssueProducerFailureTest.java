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
package org.apache.camel.component.jira.producer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.atlassian.jira.rest.client.api.RestClientException;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jira.JiraConstants.ISSUE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Uses the real Jira REST client against a local HTTP server: a request that Jira rejects must fail the exchange.
 */
class DeleteIssueProducerFailureTest extends CamelTestSupport {

    private HttpServer jira;
    private volatile int status;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    @Override
    protected void setupResources() throws Exception {
        jira = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        jira.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            byte[] body = status == 204
                    ? new byte[0]
                    : "{\"errorMessages\":[\"You do not have permission to delete issues in this project.\"],\"errors\":{}}"
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        jira.start();
    }

    @Override
    protected void cleanupResources() {
        jira.stop(0);
    }

    @Test
    void failsTheExchangeWhenJiraRejectsTheRequest() {
        status = 403;

        Exchange exchange = template.request("direct:start", e -> e.getIn().setHeader(ISSUE_KEY, "TST-1"));

        assertEquals(List.of("DELETE /rest/api/latest/issue/TST-1"), requests);
        RestClientException e = assertInstanceOf(RestClientException.class, exchange.getException());
        assertEquals(403, e.getStatusCode().get());
    }

    @Test
    void completesTheExchangeWhenJiraDeletedTheIssue() {
        status = 204;

        Exchange exchange = template.request("direct:start", e -> e.getIn().setHeader(ISSUE_KEY, "TST-1"));

        assertEquals(List.of("DELETE /rest/api/latest/issue/TST-1"), requests);
        assertNull(exchange.getException());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("jira:deleteIssue?jiraUrl=http://localhost:" + jira.getAddress().getPort()
                            + "&username=user&password=secret");
            }
        };
    }
}
