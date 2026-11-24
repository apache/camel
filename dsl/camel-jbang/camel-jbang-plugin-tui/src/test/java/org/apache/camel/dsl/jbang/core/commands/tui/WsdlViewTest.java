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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import org.apache.camel.dsl.jbang.wsdl.WsdlModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class WsdlViewTest {

    @TempDir
    Path dir;

    @Test
    void theLocationOfTheWsdlUrlInTheFolderOfTheIntegration() throws Exception {
        Files.copy(Paths.get("src/test/resources/wsdl/orders.wsdl"), dir.resolve("orders.wsdl"));
        Files.createDirectories(dir.resolve("src/main/resources"));
        Files.copy(Paths.get("src/test/resources/wsdl/orders.wsdl"), dir.resolve("src/main/resources/stock.wsdl"));
        String d = dir.toString();

        assertThat(WsdlView.location("file:orders.wsdl", d)).isEqualTo(dir.resolve("orders.wsdl").toString());
        assertThat(WsdlView.location("classpath:orders.wsdl", d)).isEqualTo(dir.resolve("orders.wsdl").toString());
        assertThat(WsdlView.location("orders.wsdl", d)).isEqualTo(dir.resolve("orders.wsdl").toString());
        // a Maven project
        assertThat(WsdlView.location("classpath:stock.wsdl", d))
                .isEqualTo(dir.resolve("src/main/resources/stock.wsdl").toString());
        assertThat(WsdlView.location("file:" + dir.resolve("orders.wsdl"), "/elsewhere"))
                .isEqualTo(dir.resolve("orders.wsdl").toString());
        assertThat(WsdlView.location("https://example.com/orders?wsdl", d)).isEqualTo("https://example.com/orders?wsdl");

        assertThat(WsdlView.location("classpath:nope.wsdl", d)).isNull();
        assertThat(WsdlView.location("{{wsdl.url}}", d)).isNull();
        assertThat(WsdlView.location("bean:wsdl", d)).isNull();
        assertThat(WsdlView.location(null, d)).isNull();
    }

    @Test
    void theOperationsOfTheWsdlAsMarkdown() throws Exception {
        String location = "src/test/resources/wsdl/orders.wsdl";
        String md = WsdlView.toMarkdown("classpath:orders.wsdl", location, WsdlModel.load(location));

        assertThat(md).contains("## WSDL classpath:orders.wsdl",
                "### OrderService · port OrderPort",
                "SOAP 1.1, document style, at `http://localhost:18081/orders`",
                "| Operation | Request | Response | Faults |",
                "| **getOrder** | getOrder | getOrderResponse | orderNotFound |",
                "| **placeOrder** | placeOrder | placeOrderResponse |  |",
                "camel generate wsdl " + location + " --samples");
    }

    @Test
    void theValuesOfTheWsdlCannotBreakTheTable() {
        assertThat(WsdlView.escape("a|b`c\nd")).isEqualTo("a\\|b'c?d");
        assertThat(WsdlView.escape(null)).isEmpty();
    }

    @Test
    void theWsdlIsReadOnAThreadOfItsOwn() throws Exception {
        WsdlView view = new WsdlView();
        String location = Paths.get("src/test/resources/wsdl/orders.wsdl").toAbsolutePath().toString();

        String first = view.markdown("classpath:orders.wsdl", location);
        assertThat(first).isIn(WsdlView.LOADING, WsdlView.toMarkdown("classpath:orders.wsdl", location,
                WsdlModel.load(location)));
        await().atMost(Duration.ofSeconds(10))
                .until(() -> !WsdlView.LOADING.equals(view.markdown("classpath:orders.wsdl", location)));
        assertThat(view.markdown("classpath:orders.wsdl", location)).contains("getOrderResponse");
    }
}
