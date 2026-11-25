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
package org.apache.camel.dsl.yaml.validator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.networknt.schema.Error;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the body of each route comes from, as {@code camel overview} shows it (CAMEL-25200), and that its findings are
 * the ones of the body check of {@code camel validate}.
 */
public class BodyOriginTest {

    private static YamlValidator validator;

    @BeforeAll
    public static void setup() throws Exception {
        validator = new YamlValidator();
        validator.init();
    }

    @Test
    public void testConsumersCallersAndRest() throws Exception {
        String yaml = """
                - rest:
                    path: /stock
                    get:
                      - path: /{sku}
                        to: direct:getStock
                    post:
                      - to: direct:addStock
                - route:
                    id: getStock
                    from:
                      uri: direct:getStock
                      steps:
                        - log: "${header.sku}"
                        - setBody:
                            constant: "{}"
                - route:
                    id: addStock
                    from:
                      uri: direct:addStock
                      steps:
                        - log: "${body}"
                - route:
                    id: nightly
                    from:
                      uri: timer:nightly
                      steps:
                        - to: direct:buildReport
                - route:
                    id: buildReport
                    from:
                      uri: direct:buildReport
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$.total"
                - route:
                    id: receive
                    from:
                      uri: platform-http:/orders
                      steps:
                        - to: direct://lookup
                - route:
                    id: lookup
                    from:
                      uri: direct:lookup
                      steps:
                        - log: "${body}"
                - route:
                    id: helper
                    from:
                      uri: direct:helper
                      steps:
                        - log: "${body}"
                """;
        Map<String, BodyOrigin> byRoute = byRoute(validator.bodyOrigins(yaml, ProjectFacts.NONE));

        BodyOrigin getStock = byRoute.get("getStock");
        assertThat(getStock.origin()).isEqualTo(BodyOrigin.REST);
        assertThat(getStock.via()).isEqualTo("GET /stock/{sku}");
        assertThat(getStock.noBody()).isTrue();
        assertThat(getStock.setBy()).isEqualTo("setBody");
        assertThat(getStock.setAt()).isEqualTo(2);
        assertThat(getStock.problem()).isNull();

        BodyOrigin addStock = byRoute.get("addStock");
        assertThat(addStock.origin()).isEqualTo(BodyOrigin.REST);
        assertThat(addStock.via()).isEqualTo("POST /stock");
        assertThat(addStock.noBody()).isFalse();

        BodyOrigin nightly = byRoute.get("nightly");
        assertThat(nightly.origin()).isEqualTo(BodyOrigin.CONSUMER);
        assertThat(nightly.noBody()).isTrue();
        assertThat(nightly.index()).isEqualTo(2);

        BodyOrigin report = byRoute.get("buildReport");
        assertThat(report.origin()).isEqualTo(BodyOrigin.CALLER);
        assertThat(report.via()).isEqualTo("direct:buildReport");
        assertThat(report.callers()).containsExactly("nightly");
        assertThat(report.noBody()).isTrue();
        assertThat(report.problem()).contains("route buildReport: jsonpath reads the message body");

        assertThat(byRoute.get("receive").origin()).isEqualTo(BodyOrigin.CONSUMER);
        assertThat(byRoute.get("receive").noBody()).isFalse();
        BodyOrigin lookup = byRoute.get("lookup");
        assertThat(lookup.origin()).isEqualTo(BodyOrigin.CALLER);
        assertThat(lookup.callers()).containsExactly("receive");
        assertThat(lookup.noBody()).isFalse();

        assertThat(byRoute.get("helper").origin()).isEqualTo(BodyOrigin.UNKNOWN);
        assertThat(byRoute.get("helper").noBody()).isFalse();
    }

    @Test
    public void testRestPathWithATrailingSlash() throws Exception {
        String yaml = """
                - rest:
                    path: /stock/
                    get:
                      - path: /{sku}
                        to: direct:getStock
                - route:
                    id: getStock
                    from:
                      uri: direct:getStock
                      steps:
                        - setBody:
                            constant: "{}"
                """;
        Map<String, BodyOrigin> byRoute = byRoute(validator.bodyOrigins(yaml, ProjectFacts.NONE));
        assertThat(byRoute.get("getStock").via()).isEqualTo("GET /stock/{sku}");
    }

    @Test
    public void testFromWithoutAnEndpoint() {
        // the schema rejects it, so only a caller of the analysis itself can hand one over: no NullPointerException
        String yaml = """
                - route:
                    id: nowhere
                    from:
                      steps:
                        - log: "${body}"
                """;
        JsonNode target = YamlTrees.newMapper().readTree(yaml);
        List<BodyOrigin> origins
                = BodyTypeFlow.analyze(target, Set.of(), new CatalogBodyTypes(new DefaultCamelCatalog(), null));
        assertThat(origins).singleElement().satisfies(o -> {
            assertThat(o.origin()).isEqualTo(BodyOrigin.UNKNOWN);
            assertThat(o.noBody()).isFalse();
        });
    }

    @Test
    public void testAnOpenApiOperationWithoutABody() throws Exception {
        String yaml = """
                - route:
                    id: getStock
                    from:
                      uri: direct:getStock
                      steps:
                        - log: "${header.sku}"
                """;
        BodyOrigin origin = validator.bodyOrigins(yaml, new ProjectFacts(Set.of("direct:getStock"), Map.of(), null))
                .get(0);
        assertThat(origin.origin()).isEqualTo(BodyOrigin.REST);
        assertThat(origin.via()).isNull();
        assertThat(origin.noBody()).isTrue();
    }

    @Test
    public void testErrorPathsAndSagas() throws Exception {
        String yaml = """
                - errorHandler:
                    deadLetterChannel:
                      deadLetterUri: direct://dlq
                - onException:
                    exception:
                      - java.io.IOException
                    steps:
                      - to: direct:failed
                - onCompletion:
                    steps:
                      - to: direct:done
                - route:
                    id: order
                    errorHandler:
                      deadLetterChannel:
                        deadLetterUri: direct:orderDlq
                    from:
                      uri: timer:order
                      steps:
                        - saga:
                            compensation: direct:cancel
                            completion: direct:confirm
                            steps:
                              - setBody:
                                  constant: order
                - route:
                    id: dlq
                    from:
                      uri: direct:dlq
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$.id"
                - route:
                    id: orderDlq
                    from:
                      uri: direct:orderDlq
                      steps:
                        - log: "${body}"
                - route:
                    id: failed
                    from:
                      uri: direct:failed
                      steps:
                        - log: "${body}"
                - route:
                    id: done
                    from:
                      uri: direct:done
                      steps:
                        - log: "${body}"
                - route:
                    id: cancel
                    from:
                      uri: direct:cancel
                      steps:
                        - log: "${header.Long-Running-Action}"
                - route:
                    id: confirm
                    from:
                      uri: direct:confirm
                      steps:
                        - log: "${header.Long-Running-Action}"
                """;
        Map<String, BodyOrigin> byRoute = byRoute(validator.bodyOrigins(yaml, ProjectFacts.NONE));

        // the failed message: its body is the one of wherever the route was, so nothing is said of it
        BodyOrigin dlq = byRoute.get("dlq");
        assertThat(dlq.origin()).isEqualTo(BodyOrigin.ERROR_HANDLER);
        assertThat(dlq.via()).isEqualTo("deadLetterChannel");
        assertThat(dlq.callers()).isEmpty();
        assertThat(dlq.noBody()).isFalse();
        assertThat(dlq.problem()).isNull();

        BodyOrigin orderDlq = byRoute.get("orderDlq");
        assertThat(orderDlq.origin()).isEqualTo(BodyOrigin.ERROR_HANDLER);
        assertThat(orderDlq.callers()).containsExactly("order");

        assertThat(byRoute.get("failed").origin()).isEqualTo(BodyOrigin.ERROR_HANDLER);
        assertThat(byRoute.get("failed").via()).isEqualTo("onException");
        assertThat(byRoute.get("done").origin()).isEqualTo(BodyOrigin.ON_COMPLETION);

        BodyOrigin cancel = byRoute.get("cancel");
        assertThat(cancel.origin()).isEqualTo(BodyOrigin.SAGA);
        assertThat(cancel.via()).isEqualTo("compensation");
        assertThat(cancel.callers()).containsExactly("order");
        assertThat(byRoute.get("confirm").via()).isEqualTo("completion");

        // and camel validate says nothing of the dead letter route either
        assertThat(validator.validate(yaml).stream().map(Error::getMessage))
                .noneMatch(m -> m.contains("reads the message body"));
    }

    @Test
    public void testTheFindingsAreTheOnesOfValidate() throws Exception {
        String yaml = """
                - route:
                    id: tick
                    from:
                      uri: timer:tick
                      steps:
                        - to: direct:lookup
                - route:
                    id: tock
                    from:
                      uri: timer:tock
                      steps:
                        - to: direct:convert
                - route:
                    id: lookup
                    from:
                      uri: direct:lookup
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$.sku"
                - route:
                    id: convert
                    from:
                      uri: direct:convert
                      steps:
                        - unmarshal:
                            json: {}
                """;
        List<String> problems = validator.bodyOrigins(yaml, ProjectFacts.NONE).stream()
                .map(BodyOrigin::problem).filter(Objects::nonNull).toList();
        List<String> validate = validator.validate(yaml).stream().map(Error::getMessage)
                .filter(m -> m.contains("the message reaching this route has none")).toList();
        assertThat(problems).hasSize(2);
        assertThat(validate).hasSize(2);
        for (int i = 0; i < problems.size(); i++) {
            assertThat(validate.get(i)).endsWith(problems.get(i));
        }
    }

    @Test
    public void testNothingWhenTheSchemaFails() throws Exception {
        // the routes of YAML that does not pass the schema are not checked by camel validate: no origins either
        String yaml = """
                - route:
                    id: tick
                    from:
                      uri: timer:tick
                      steps:
                        - noSuchStep: {}
                """;
        assertThat(validator.bodyOrigins(yaml, ProjectFacts.NONE)).isEmpty();
        assertThat(validator.bodyOrigins("", ProjectFacts.NONE)).isEmpty();
        assertThat(validator.bodyOrigins("- route: [", ProjectFacts.NONE)).isEmpty();
    }

    private static Map<String, BodyOrigin> byRoute(List<BodyOrigin> origins) {
        Map<String, BodyOrigin> answer = new LinkedHashMap<>();
        origins.forEach(o -> answer.put(o.route(), o));
        return answer;
    }
}
