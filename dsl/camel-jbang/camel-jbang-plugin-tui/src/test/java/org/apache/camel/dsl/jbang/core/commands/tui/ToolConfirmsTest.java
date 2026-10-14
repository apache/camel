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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * The AI tools run without asking, except a call that reaches beyond the integration, may destroy data or runs code.
 */
class ToolConfirmsTest {

    // as the status of an integration lists its endpoints (Endpoint.getEndpointUri)
    private static final List<String> ENDPOINTS = List.of(
            "kafka://orders?brokers=localhost%3A9092&groupId=shop", "direct://charge", "timer://tick?period=1000");

    @Test
    void aMessageToAnEndpointOfTheIntegrationIsSentWithoutAsking() {
        // the same endpoint, written another way: the URIs are compared normalized
        assertThat(ToolConfirms.sendMessage("kafka:orders?groupId=shop&brokers=localhost:9092", ENDPOINTS, null)).isNull();
        assertThat(ToolConfirms.sendMessage("timer:tick?period=1000", ENDPOINTS, null)).isNull();
        // a route id or a pattern: the send console picks one of the integration's routes
        assertThat(ToolConfirms.sendMessage("checkout", ENDPOINTS, null)).isNull();
        assertThat(ToolConfirms.sendMessage("kafka:*", ENDPOINTS, null)).isNull();
        // an endpoint of a component the catalog marks internal stays inside the integration: its routes, in-memory
        // state, log and test endpoints
        assertThat(ToolConfirms.sendMessage("direct:unknown", ENDPOINTS, null)).isNull();
        assertThat(ToolConfirms.sendMessage("seda:work", List.of(), null)).isNull();
        assertThat(ToolConfirms.sendMessage("log:demo?showAll=true", List.of(), null)).isNull();
        assertThat(ToolConfirms.sendMessage("mock:result", List.of(), null)).isNull();
        assertThat(ToolConfirms.sendMessage("stub:kafka:payments", List.of(), null)).isNull();
        assertThat(ToolConfirms.sendMessage("caffeine-cache:orders?action=PUT&key=1", List.of(), null)).isNull();
    }

    @Test
    void aMessageToAnEndpointTheIntegrationDoesNotHaveAsks() {
        ToolConfirms.Call call = ToolConfirms.sendMessage("file:/Users/me?fileName=.zshrc", ENDPOINTS, null);

        assertThat(call).isNotNull();
        assertThat(call.target()).isEqualTo("file:/Users/me?fileName=.zshrc");
        assertThat(call.title()).isEqualTo("send a message to an endpoint the integration does not have");
        assertThat(ToolConfirms.sendMessage("exec:rm?args=-rf /tmp/x", ENDPOINTS, null)).isNotNull();
        assertThat(ToolConfirms.sendMessage("http://example.com/hook", ENDPOINTS, null)).isNotNull();
        // another topic, or the endpoint of the integration with an option of its own, is not the same endpoint
        assertThat(ToolConfirms.sendMessage("kafka:payments?brokers=localhost:9092&groupId=shop", ENDPOINTS, null)).isNotNull();
        assertThat(ToolConfirms.sendMessage("kafka:orders?brokers=evil:9092&groupId=shop", ENDPOINTS, null)).isNotNull();
        // nothing known of the integration: everything outside it asks
        assertThat(ToolConfirms.sendMessage("kafka:orders", List.of(), null)).isNotNull();
    }

    @Test
    void aBodyReadFromAFileInTheProjectIsSentWithoutAsking(@TempDir Path project) throws IOException {
        Files.createDirectories(project.resolve("data"));
        Files.writeString(project.resolve("data/order.json"), "{}");
        String work = project.toString();

        // relative to the working directory of the integration, or absolute
        assertThat(ToolConfirms.fileOutsideProject("file:data/order.json", work, project)).isNull();
        assertThat(ToolConfirms.fileOutsideProject("file:" + project.resolve("data/order.json"), work, project)).isNull();
        // a file that is not there yet
        assertThat(ToolConfirms.fileOutsideProject("file:data/new.json", work, project)).isNull();
        // not a file
        assertThat(ToolConfirms.fileOutsideProject("hello file:x", work, project)).isNull();
        assertThat(ToolConfirms.fileOutsideProject(null, work, project)).isNull();
    }

    @Test
    void aBodyReadFromAFileOutsideTheProjectAsks(@TempDir Path tmp) throws IOException {
        Path project = Files.createDirectories(tmp.resolve("project"));
        Path secret = Files.writeString(tmp.resolve("secret.txt"), "s3cr3t");
        String work = project.toString();

        assertThat(ToolConfirms.fileOutsideProject("file:" + secret, work, project))
                .isEqualTo(secret.toRealPath().toString());
        // out of the project with .., also through a folder that is not there
        assertThat(ToolConfirms.fileOutsideProject("file:../secret.txt", work, project)).isNotNull();
        assertThat(ToolConfirms.fileOutsideProject("file:missing/../../secret.txt", work, project))
                .isEqualTo(secret.toRealPath().toString());
        // a relative file when the working directory is not known, any file when the project is not known
        assertThat(ToolConfirms.fileOutsideProject("file:data/order.json", null, project)).isEqualTo("data/order.json");
        assertThat(ToolConfirms.fileOutsideProject("file:data/order.json", work, null)).isNotNull();

        // wherever the message goes, even to the log
        ToolConfirms.Call call = ToolConfirms.sendMessage("log:demo", List.of(), secret.toString());
        assertThat(call.title()).isEqualTo("send a file from outside the project");
        assertThat(call.target()).isEqualTo("log:demo\nbody: file:" + secret);
        assertThat(ToolConfirms.sendMessage("http://example.com/hook", List.of(), secret.toString()).title())
                .isEqualTo("send a file from outside the project to an endpoint the integration does not have");
    }

    @Test
    void aSymlinkInTheProjectToAFileOutsideAsks(@TempDir Path tmp) throws IOException {
        Path project = Files.createDirectories(tmp.resolve("project"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "s3cr3t");
        boolean linked;
        try {
            Files.createSymbolicLink(project.resolve("link"), outside);
            linked = true;
        } catch (UnsupportedOperationException | IOException e) {
            linked = false;
        }
        assumeThat(linked).as("symbolic links").isTrue();

        assertThat(ToolConfirms.fileOutsideProject("file:link/secret.txt", project.toString(), project)).isNotNull();
    }

    @Test
    void anInternalEndpointThatReachesOutAsks() {
        // a bean option runs any constructor, a placeholder can log a secret
        assertThat(ToolConfirms.sendMessage("log:x?exchangeFormatter=#class:java.io.FileOutputStream('/tmp/x')",
                List.of(), null)).isNotNull();
        assertThat(ToolConfirms.sendMessage("seda:x?queue=%23bean:queue", List.of(), null)).isNotNull();
        assertThat(ToolConfirms.sendMessage("log:{{aws.secretKey}}", List.of(), null)).isNotNull();
        // not remote, but not internal: they run code, or read a file or a URL
        assertThat(ToolConfirms.sendMessage("bean:orderService?method=deleteAll", List.of(), null)).isNotNull();
        assertThat(ToolConfirms.sendMessage("class:java.lang.Runtime", List.of(), null)).isNotNull();
        assertThat(ToolConfirms.sendMessage("language:groovy:'touch /tmp/x'.execute()", List.of(), null)).isNotNull();
        assertThat(ToolConfirms.sendMessage("velocity:file:/Users/me/.ssh/id_rsa", List.of(), null)).isNotNull();
        // they look internal but are not, as a seldom used option or feature reaches out: dataset-test polls the
        // endpoint in its URI (a file consumer moves the files it reads), micrometer evaluates its options with simple,
        // which loads a resource: text from a file or a URL
        assertThat(ToolConfirms.sendMessage("dataset-test:file:/Users/me/work", List.of(), null)).isNotNull();
        assertThat(ToolConfirms.sendMessage("micrometer:counter:resource:file:/Users/me/.aws/credentials", List.of(), null))
                .isNotNull();
        // a component the catalog does not know, such as a custom one
        assertThat(ToolConfirms.sendMessage("acme:orders", List.of(), null)).isNotNull();
        assertThat(ToolConfirms.isInternal("../components/log")).isFalse();
    }

    @Test
    void sqlThatOnlyReadsRunsWithoutAsking() {
        assertThat(ToolConfirms.sql("SELECT * FROM orders WHERE status = 'open'")).isNull();
        assertThat(ToolConfirms.sql("  select count(*) from orders;  ")).isNull();
        assertThat(ToolConfirms.sql("-- the open orders\n/* all */ (SELECT id FROM orders)")).isNull();
        // a semicolon or a keyword in a literal or a quoted name is text
        assertThat(ToolConfirms.sql("SELECT * FROM notes WHERE text = 'a; DROP TABLE x' OR \"into\" = 1")).isNull();
        assertThat(ToolConfirms.sql("SHOW TABLES")).isNull();
        assertThat(ToolConfirms.sql("DESCRIBE orders")).isNull();
    }

    @Test
    void sqlThatIsNotASelectAsks() {
        assertThat(ToolConfirms.sql("DROP TABLE orders")).isNotNull();
        assertThat(ToolConfirms.sql("delete from orders")).isNotNull();
        assertThat(ToolConfirms.sql("UPDATE orders SET status = 'x'")).isNotNull();
        assertThat(ToolConfirms.sql("INSERT INTO orders VALUES (1)")).isNotNull();
        // a second statement, a SELECT that creates a table, and EXPLAIN ANALYZE, which runs the statement
        assertThat(ToolConfirms.sql("SELECT 1; DROP TABLE orders")).isNotNull();
        assertThat(ToolConfirms.sql("SELECT * INTO backup FROM orders")).isNotNull();
        assertThat(ToolConfirms.sql("EXPLAIN ANALYZE DELETE FROM orders")).isNotNull();
        assertThat(ToolConfirms.sql("WITH gone AS (DELETE FROM orders RETURNING *) SELECT * FROM gone")).isNotNull();
        assertThat(ToolConfirms.sql("")).isNotNull();
        assertThat(ToolConfirms.updateRow("orders", "{\"status\":\"x\"}").title()).isEqualTo("update a row of orders");
    }

    @Test
    void anExpressionInALanguageThatRunsCodeAsks() {
        ToolConfirms.Call call = ToolConfirms.evalExpression("Groovy", "'rm -rf /tmp/x'.execute()");

        assertThat(call).isNotNull();
        assertThat(call.title()).isEqualTo("evaluate a Groovy expression");
        assertThat(ToolConfirms.evalExpression("java", "1 + 1")).isNotNull();
        assertThat(ToolConfirms.evalExpression("js", "1 + 1")).isNotNull();
        // the languages that only read the message do not
        assertThat(ToolConfirms.evalExpression("simple", "${body}")).isNull();
        assertThat(ToolConfirms.evalExpression("jsonpath", "$.id")).isNull();
        assertThat(ToolConfirms.evalExpression(null, "${body}")).isNull();
    }
}
