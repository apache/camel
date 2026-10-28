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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24992: a rest-openapi call with no header for a path parameter of its operation is reported before the route
 * runs, and the check stays quiet where it cannot prove the header is missing.
 */
class OpenApiPathParamsTest {

    private static final String SPEC = """
            {"openapi": "3.0.3", "info": {"title": "Stock", "version": "1"},
             "paths": {"/stock/{sku}/reserve": {"post": {"operationId": "reserveStock",
                "parameters": [{"name": "sku", "in": "path", "required": true, "schema": {"type": "string"}}]}},
                       "/stock": {"get": {"operationId": "listStock"}}}}
            """;

    @TempDir
    Path dir;

    @BeforeEach
    void spec() throws IOException {
        Files.writeString(dir.resolve("stock-api.json"), SPEC);
    }

    private static String route(String from, String beforeCall, String call) {
        return "- route:\n    id: reserve-order-lines\n    from:\n      uri: " + from + "\n      steps:\n"
               + "        - split:\n            expression:\n              jsonpath: '$.lines'\n            steps:\n"
               + beforeCall
               + "              - to:\n                  uri: " + call + "\n";
    }

    private static final String SET_PROPERTY = "              - setProperty:\n                  name: sku\n"
                                               + "                  expression:\n                    simple: ${body.sku}\n";
    private static final String SET_HEADER = "              - setHeader:\n                  name: sku\n"
                                             + "                  expression:\n                    simple: ${body.sku}\n";
    private static final String CALL = "rest-openapi:stock-api.json#reserveStock";

    @Test
    void aPathParameterWithNoHeaderIsReportedAtTheCall() {
        // the benchmark's model kept the sku in an exchange property, not a header (r7, run q8)
        List<String> msgs = OpenApiPathParams.validate(route("file:orders", SET_PROPERTY, CALL), dir);

        assertThat(msgs).singleElement().asString()
                .startsWith("Line 15: the operation reserveStock (/stock/{sku}/reserve) needs the path parameter sku,")
                .contains("the request goes out with {sku} in the path")
                .contains("(the exchange property sku is not used for the path)")
                .contains("setHeader");
    }

    @Test
    void theOperationGivenAsOptionsIsCheckedToo() {
        String route
                = "- route:\n    from:\n      uri: timer:tick\n      steps:\n        - to:\n            uri: rest-openapi\n"
                  + "            parameters:\n              specificationUri: stock-api.json\n"
                  + "              operationId: reserveStock\n";

        assertThat(OpenApiPathParams.validate(route, dir)).singleElement().asString()
                .startsWith("Line 6: the operation reserveStock (/stock/{sku}/reserve) needs the path parameter sku");
    }

    @Test
    void aHeaderAVariableOrAnOptionOfTheNameIsEnough() {
        assertThat(OpenApiPathParams.validate(route("file:orders", SET_HEADER, CALL), dir)).isEmpty();
        assertThat(OpenApiPathParams.validate(route("file:orders",
                "              - setVariable:\n                  name: sku\n                  constant: x\n", CALL), dir))
                .isEmpty();
        assertThat(OpenApiPathParams.validate(route("timer:tick", "", CALL + "?sku=CAMEL-MUG"), dir)).isEmpty();
        // an operation without a path parameter
        assertThat(OpenApiPathParams.validate(route("timer:tick", "", "rest-openapi:stock-api.json#listStock"), dir))
                .isEmpty();
    }

    @Test
    void quietWhereTheHeaderCouldComeFromSomewhereElse() {
        // a caller of a direct route can set the header
        assertThat(OpenApiPathParams.validate(route("direct:reserve", SET_PROPERTY, CALL), dir)).isEmpty();
        // a bean the walk cannot follow
        assertThat(OpenApiPathParams.validate(route("file:orders",
                "              - bean:\n                  ref: skuSetter\n", CALL), dir)).isEmpty();
        // an operation the specification does not have, or no specification beside the route
        assertThat(OpenApiPathParams.validate(route("file:orders", "", "rest-openapi:stock-api.json#nope"), dir))
                .isEmpty();
        assertThat(OpenApiPathParams.validate(route("file:orders", "", "rest-openapi:other.json#reserveStock"), dir))
                .isEmpty();
    }

    @Test
    void theValidatorOfAWriteReportsIt() {
        List<String> msgs = SourceValidator.validate("openapi-client.camel.yaml", route("file:orders", SET_PROPERTY, CALL),
                new DefaultCamelCatalog(), null, dir);

        assertThat(msgs).anyMatch(m -> m.contains("needs the path parameter sku"));
    }
}
