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
package org.apache.camel.component.docling;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27202: when a conversion fails on the server, the sync endpoint of docling-serve answers with a 404 "Task
 * result not found" instead of the error. The producer says that the conversion failed on the server, using a stub
 * docling-serve instead of a real one.
 */
class DoclingServeSyncFailureTest extends CamelTestSupport {

    private static final String SOURCE = "https://example.com/report.pdf";
    private static final String TASK_RESULT_NOT_FOUND
            = "{\"detail\":\"Task result not found. Please wait for a completion status.\"}";

    private HttpServer server;

    @AfterEach
    void stopStubServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void aLostSyncResultSaysThatTheConversionFailedOnTheServer() throws Exception {
        startStubServer(404, TASK_RESULT_NOT_FOUND);

        Exception exception = convert("CONVERT_TO_MARKDOWN");

        assertThat(exception).isInstanceOf(IOException.class)
                .hasMessageContaining("the conversion failed on the docling-serve server")
                .hasMessageContaining("docling-serve log")
                .hasMessageContaining("useAsyncMode=true");
    }

    @Test
    void aLostSyncResultOfAJsonConversionSaysThatTheConversionFailedOnTheServer() throws Exception {
        startStubServer(404, TASK_RESULT_NOT_FOUND);

        Exception exception = convert("CONVERT_TO_JSON");

        assertThat(exception).isInstanceOf(IOException.class)
                .hasMessageContaining("the conversion failed on the docling-serve server");
    }

    @Test
    void otherErrorsKeepTheMessageOfTheServer() throws Exception {
        startStubServer(500, "{\"detail\":\"Internal Server Error\"}");

        Exception exception = convert("CONVERT_TO_MARKDOWN");

        assertThat(exception).isInstanceOf(IOException.class)
                .hasMessageStartingWith("Failed to convert document: ")
                .hasMessageContaining("Internal Server Error")
                .hasMessageNotContaining("docling-serve log");
    }

    private Exception convert(String operation) {
        String uri = "docling:convert?operation=" + operation
                     + "&useDoclingServe=true&allowUrlSource=true&doclingServeUrl=http://localhost:"
                     + server.getAddress().getPort();
        Exchange exchange = template.send(uri, e -> e.getIn().setBody(SOURCE));

        return exchange.getException();
    }

    private void startStubServer(int status, String answer) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }
}
