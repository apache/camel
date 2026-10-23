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
package org.apache.camel.component.aws2.redshift.data;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.redshiftdata.model.ListDatabasesResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The producer uses the client that the endpoint builds from the access key and the secret key, when no
 * RedshiftDataClient is given. The calls go to a local stub of the Redshift Data API.
 */
public class RedshiftData2ProducerOwnClientTest extends CamelTestSupport {

    private final List<String> targets = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @Override
    protected void setupResources() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            targets.add(exchange.getRequestHeaders().getFirst("X-Amz-Target"));
            byte[] answer = "{\"Databases\":[\"dev\",\"prod\"]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/x-amz-json-1.1");
            exchange.sendResponseHeaders(200, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        server.start();
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void listDatabasesWithTheClientOfTheEndpoint() {
        Object body = template.requestBody("direct:listDatabases", "");

        ListDatabasesResponse response = assertInstanceOf(ListDatabasesResponse.class, body);
        assertEquals(List.of("dev", "prod"), response.databases());
        assertEquals(List.of("RedshiftData.ListDatabases"), targets);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:listDatabases")
                        .to("aws2-redshift-data://test?operation=listDatabases&accessKey=xxx&secretKey=yyy&region=eu-west-1"
                            + "&overrideEndpoint=true&uriEndpointOverride=http://localhost:" + server.getAddress().getPort());
            }
        };
    }
}
