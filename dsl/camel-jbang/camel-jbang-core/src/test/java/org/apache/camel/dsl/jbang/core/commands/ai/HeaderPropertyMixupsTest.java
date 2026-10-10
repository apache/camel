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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.List;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25516: a header, exchange property or variable read where the route keeps the name in another of them.
 */
class HeaderPropertyMixupsTest {

    private static String route(String from, String set, String read) {
        return "- route:\n    from:\n      uri: " + from + "\n      steps:\n"
               + "        - " + set + ":\n            name: sku\n            expression:\n              simple: ${body[sku]}\n"
               + "        - toD:\n            uri: \"http://localhost:8080/stock/" + read + "\"\n";
    }

    @Test
    void aHeaderReadWhereTheRouteKeepsAProperty() {
        List<String> msgs = HeaderPropertyMixups.validate(route("file:orders", "setProperty", "${header.sku}"));

        assertThat(msgs).singleElement().isEqualTo("Line 10: ${header.sku} reads a header sku, but the route keeps sku in"
                                                   + " an exchange property (the header is null here): write"
                                                   + " ${exchangeProperty.sku}");
    }

    @Test
    void aPropertyReadWhereTheRouteKeepsAHeader() {
        assertThat(HeaderPropertyMixups.validate(route("timer:tick", "setHeader", "${exchangeProperty.sku}")))
                .singleElement().asString()
                .startsWith("Line 10: ${exchangeProperty.sku} reads an exchange property sku, but the route keeps sku in"
                            + " a header")
                .endsWith("write ${header.sku}");
    }

    @Test
    void variablesAreTheThirdPlace() {
        assertThat(HeaderPropertyMixups.validate(route("file:orders", "setVariable", "${header.sku}")))
                .singleElement().asString()
                .startsWith("Line 10: ${header.sku} reads a header sku, but the route keeps sku in a variable")
                .endsWith("write ${variable.sku}");
        assertThat(HeaderPropertyMixups.validate(route("file:orders", "setProperty", "${variable.sku}")))
                .singleElement().asString().endsWith("write ${exchangeProperty.sku}");
        assertThat(HeaderPropertyMixups.validate(route("file:orders", "setVariable", "${variable.sku}"))).isEmpty();
    }

    @Test
    void theRightReadIsNotReported() {
        assertThat(HeaderPropertyMixups.validate(route("file:orders", "setProperty", "${exchangeProperty.sku}"))).isEmpty();
        assertThat(HeaderPropertyMixups.validate(route("file:orders", "setHeader", "${header.sku}"))).isEmpty();
    }

    @Test
    void quietWhereTheNameCanComeFromElsewhere() {
        // a caller, or an HTTP request, can carry the header
        assertThat(HeaderPropertyMixups.validate(route("direct:check", "setProperty", "${header.sku}"))).isEmpty();
        assertThat(HeaderPropertyMixups.validate(route("platform-http:/check", "setProperty", "${header.sku}")))
                .isEmpty();
        // a path parameter is a header of the request
        assertThat(HeaderPropertyMixups.validate(route("file:orders", "setProperty", "${header.sku}")
                                                 + "- rest:\n    get:\n      - path: /stock/{sku}\n        to: direct:x\n"))
                .isEmpty();
        // set in both forms
        String both = route("file:orders", "setProperty", "${header.sku}")
                .replace("        - toD:",
                        "        - setHeader:\n            name: sku\n            constant: x\n        - toD:");
        assertThat(HeaderPropertyMixups.validate(both)).isEmpty();
    }

    @Test
    void theValidatorOfAWriteReportsIt() {
        // the file is unmarshalled first, or the body type check reports the read of ${body[sku]} on a GenericFile
        String route = route("file:orders", "setProperty", "${header.sku}")
                .replace("      steps:\n", "      steps:\n        - unmarshal:\n            json: {}\n");
        List<String> msgs = SourceValidator.validate("http-client.camel.yaml", route, new DefaultCamelCatalog(), null);

        assertThat(msgs).anyMatch(m -> m.contains("${header.sku} reads a header sku"));
    }
}
