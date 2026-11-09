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
package org.apache.camel.dsl.jbang.run;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Citrus test is not a file to run: camel test run runs it, and camel run would load no route from it.
 */
class RunFilesTest {

    @TempDir
    Path dir;

    @ParameterizedTest
    @ValueSource(strings = {
            "orders.citrus.yaml", "orders.citrus.it.yaml", "orders.citrus.test.yaml", "orders.citrus-it.yaml",
            "orders.citrus-test.yaml", "orders.citrus.it.yml", "orders.citrus.xml", "orders.citrus.it.xml",
            "orders.citrus.groovy", "orders.citrus-it.groovy", "orders.feature", "orders.citrus.feature" })
    void aCitrusTestByItsName(String name) {
        // the names Citrus runs as a test, which no route has: the file is not read, so it need not be there
        assertThat(RunFiles.isCitrusTest(dir.resolve("test").resolve(name))).isTrue();
    }

    @Test
    void aChaosScenarioIsNotRunNorExported() {
        // camel run * and camel export: camel run --chaos=file and camel cmd chaos-load read it
        assertThat(RunFiles.skipFile("outage.chaos", List.of(), "")).isTrue();
        assertThat(RunFiles.skipFile("test/chaos/outage.chaos", List.of(), "")).isTrue();
        assertThat(RunFiles.skipFile("orders.camel.yaml", List.of(), "")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "orders.camel.yaml", "orders.yaml", "citrus.yaml", "orders.camel.xml", "Orders.java", "orders.groovy",
            "citrus-application.properties", "orders.citrus.properties" })
    void notACitrusTest(String name) {
        assertThat(RunFiles.isCitrusTest(dir.resolve(name))).isFalse();
    }

    @Test
    void aJavaOrXmlTestIsACitrusTestWhenItUsesCitrus() throws Exception {
        Path java = Files.writeString(dir.resolve("OrdersIT.java"),
                "import org.citrusframework.TestCaseRunner;\n\npublic class OrdersIT implements Runnable {\n"
                                                                    + "    public void run() {\n    }\n}\n");
        Path xml = Files.writeString(dir.resolve("OrdersTest.xml"),
                "<test name=\"OrdersTest\" xmlns=\"http://citrusframework.org/schema/xml/testcase\">\n"
                                                                    + "  <actions>\n    <echo message=\"hi\"/>\n  </actions>\n</test>\n");
        assertThat(RunFiles.isCitrusTest(java)).isTrue();
        assertThat(RunFiles.isCitrusTest(xml)).isTrue();

        // a route can have such a name as well
        Path route = Files.writeString(dir.resolve("LoadTest.java"),
                "import org.apache.camel.builder.RouteBuilder;\n\npublic class LoadTest extends RouteBuilder {\n"
                                                                     + "    public void configure() {\n"
                                                                     + "        from(\"timer:load\").to(\"log:load\");\n    }\n}\n");
        Path routes = Files.writeString(dir.resolve("LoadIT.xml"),
                "<routes xmlns=\"http://camel.apache.org/schema/xml-io\">\n  <route>\n    <from uri=\"timer:load\"/>\n"
                                                                   + "    <to uri=\"log:load\"/>\n  </route>\n</routes>\n");
        assertThat(RunFiles.isCitrusTest(route)).isFalse();
        assertThat(RunFiles.isCitrusTest(routes)).isFalse();
        // nothing to read
        assertThat(RunFiles.isCitrusTest(dir.resolve("MissingIT.java"))).isFalse();
    }

    @Test
    void theMessageSaysToRunTheTestWithCamelTestRun() {
        assertThat(RunFiles.citrusTestMessage(List.of("test/orders.citrus.it.yaml")))
                .isEqualTo("test/orders.citrus.it.yaml is a Citrus test, not a route: run it with camel test run"
                           + " test/orders.citrus.it.yaml");
        assertThat(RunFiles.citrusTestMessage(List.of("test/orders.citrus.it.yaml", "test/orders.feature")))
                .isEqualTo("test/orders.citrus.it.yaml, test/orders.feature are Citrus tests, not routes: run them with"
                           + " camel test run test/orders.citrus.it.yaml test/orders.feature");
        // a test given with routes is left out
        assertThat(RunFiles.citrusTestSkipMessage("orders.citrus.it.yaml"))
                .isEqualTo("Skipping orders.citrus.it.yaml: a Citrus test, not a route (run it with camel test run"
                           + " orders.citrus.it.yaml)");
    }

    @Test
    void aFolderOfOnlyCitrusTests() throws Exception {
        // the test folder of a project: its tests, with the files a test reads, and its Citrus configuration
        Path test = Files.createDirectories(dir.resolve("test"));
        Path orders = Files.writeString(test.resolve("orders.citrus.it.yaml"), "name: orders-test\nactions: []\n");
        Path refunds = Files.writeString(test.resolve("refunds.citrus.it.yaml"), "name: refunds-test\nactions: []\n");
        Files.writeString(test.resolve("citrus-application.properties"), "citrus.camel.jbang.version=4.24.0\n");
        Files.writeString(test.resolve("sample-order.json"), "{\"orderId\": \"ORD-1\"}\n");
        Files.writeString(test.resolve("compose.yaml"), "services:\n  broker:\n    image: apache/artemis\n");
        Files.writeString(test.resolve(".gitignore"), "target\n");
        // and the chaos scenarios, with the messages a scenario sends
        Path chaos = Files.createDirectories(test.resolve("chaos"));
        Files.writeString(chaos.resolve("outage.chaos"), "seed=42\nendpoint=http:stock* error status=503\n");
        Files.writeString(Files.createDirectories(chaos.resolve("outage")).resolve("001.yaml"), "orderId: ORD-1\n");
        assertThat(RunFiles.citrusTestsOnly(test)).containsExactly(orders, refunds);

        // a folder that holds what camel run loads: a route, the Camel configuration, or a subfolder
        Path route = Files.writeString(test.resolve("orders.camel.yaml"), "- from:\n    uri: timer:tick\n");
        assertThat(RunFiles.citrusTestsOnly(test)).isEmpty();
        Files.delete(route);
        Path props = Files.writeString(test.resolve("application.properties"), "camel.server.port=8080\n");
        assertThat(RunFiles.citrusTestsOnly(test)).isEmpty();
        Files.delete(props);
        Files.createDirectories(test.resolve("routes"));
        assertThat(RunFiles.citrusTestsOnly(test)).isEmpty();

        // a project with a test folder, a folder without tests, and a folder that is not there
        assertThat(RunFiles.citrusTestsOnly(dir)).isEmpty();
        assertThat(RunFiles.citrusTestsOnly(Files.createDirectories(dir.resolve("empty")))).isEmpty();
        assertThat(RunFiles.citrusTestsOnly(dir.resolve("missing"))).isEmpty();
    }
}
