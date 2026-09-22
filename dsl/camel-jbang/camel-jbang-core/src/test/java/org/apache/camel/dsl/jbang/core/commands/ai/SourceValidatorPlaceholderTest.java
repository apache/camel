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

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24857 and CAMEL-24858: a ${...} placeholder where {{...}} is meant (an endpoint option, a bean property), and a
 * required endpoint path option that is missing.
 */
class SourceValidatorPlaceholderTest {

    private static final CamelCatalog catalog = new DefaultCamelCatalog();

    /** CAMEL-24888: a path parameter of an OpenAPI operation is a header, not an endpoint option. */
    @Test
    void aRestOpenApiPathOptionSaysTheParameterIsAHeader() {
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to:
                            uri: rest-openapi
                            parameters:
                              specificationUri: stock-api.json
                              operationId: reserveStock
                              path: "sku=${exchangeProperty.sku}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).anyMatch(e -> e.contains("rest-openapi: Unknown option 'path'")
                && e.contains("comes from a header of the same name: add setHeader: {name: sku"));
        // the sibling spellings say the same
        for (String option : List.of("pathParameters", "queryParameters")) {
            List<String> more
                    = SourceValidator.validateYamlEndpoints(yaml.replace("path: \"sku=", option + ": \"sku="), catalog);
            assertThat(more).anyMatch(e -> e.contains("rest-openapi: Unknown option '" + option + "'")
                    && e.contains("comes from a header of the same name: add setHeader: {name: "));
        }
    }

    @Test
    void aSimplePlaceholderInAnEndpointOptionSaysToWriteAPropertyPlaceholder() {
        String yaml = """
                - route:
                    from:
                      uri: timer
                      parameters:
                        timerName: welcome
                        period: "${welcome.period}"
                      steps:
                        - to:
                            uri: "log:done?level=${properties:log.level}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).anyMatch(e -> e.startsWith("Line 6: timer: period=${welcome.period} is a Simple expression")
                && e.endsWith("so period: \"{{welcome.period}}\""));
        assertThat(errors).anyMatch(e -> e.startsWith("Line 9: log: level=${properties:log.level} is a Simple expression")
                && e.endsWith("so level: \"{{log.level}}\""));
    }

    @Test
    void aDynamicEipAndAPropertyPlaceholderAreFine() {
        String yaml = """
                - route:
                    from:
                      uri: timer
                      parameters:
                        timerName: welcome
                        period: "{{welcome.period}}"
                      steps:
                        - toD:
                            uri: "log:${header.target}?level=INFO"
                """;
        assertThat(SourceValidator.validateYamlEndpoints(yaml, catalog)).noneMatch(e -> e.contains("Simple expression"));
    }

    @Test
    void aMissingRequiredPathOptionIsReportedWithBothPlaces() {
        String yaml = """
                - route:
                    from:
                      uri: cron
                      parameters:
                        schedule: 0/10 * * * * ?
                      steps:
                        - to:
                            uri: log:done
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).anyMatch(e -> e.startsWith("Line 3: cron: the required option 'name' is missing")
                && e.contains("uri: cron:<name>") && e.contains("name: <value>"));
        // given in the uri, or as a parameter: fine
        assertThat(SourceValidator.validateYamlEndpoints(yaml.replace("uri: cron", "uri: cron:report"), catalog))
                .noneMatch(e -> e.contains("required option"));
        assertThat(SourceValidator.validateYamlEndpoints(yaml.replace("schedule: 0/10", "name: report\n        schedule: 0/10"),
                catalog))
                .noneMatch(e -> e.contains("required option"));
    }

    @Test
    void aSimplePlaceholderInABeanPropertySaysToWriteAPropertyPlaceholder() {
        String yaml = """
                - beans:
                    - name: orderNumber
                      type: "camel.example.OrderNumber"
                      properties:
                        start: ${order.first-number}
                        label: "{{order.label}}"
                """;
        List<String> msgs = BeanRefChecks.validateBeanPropertyPlaceholders(yaml);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).startsWith("Line 5: start: ${order.first-number} is a Simple expression")
                .endsWith("so start: \"{{order.first-number}}\"");
    }
}
