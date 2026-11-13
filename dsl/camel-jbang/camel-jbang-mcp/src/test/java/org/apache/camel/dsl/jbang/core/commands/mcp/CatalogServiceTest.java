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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.Optional;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogServiceTest {

    private static final String BUILTIN_VERSION = new DefaultCamelCatalog().getCatalogVersion();

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Runs only local — requires Maven Central access")
    void mixedRuntimeRequestsKeepTheirCoreSchemasAndProviderCoordinates() throws Exception {
        CatalogService service = new CatalogService();
        service.catalogRepos = Optional.empty();
        String targetSplit = service.loadCatalog("main", "4.8.0", null).modelJSonSchema("split");
        ConfigurationValidateTools tools = new ConfigurationValidateTools();
        tools.catalogService = service;
        String[][] requests = {
                { "spring-boot", "4.8.0", null, "camel-http-starter", "4.8.0" },
                { "quarkus", null, "io.quarkus.platform:quarkus-camel-bom:3.15.1", "camel-quarkus-http", "3.15.0" },
                { "main", "4.8.0", null, "camel-http", "4.8.0" },
                {
                        "spring-boot", "4.8.0", "org.apache.camel.springboot:camel-spring-boot-bom:4.8.0",
                        "camel-http-starter", "4.8.0" }
        };
        for (int pass = 0; pass < 2; pass++) {
            for (String[] request : requests) {
                CamelCatalog catalog = service.loadCatalog(request[0], request[1], request[2]);
                assertThat(catalog.getCatalogVersion()).isEqualTo("4.8.0");
                assertThat(catalog.modelJSonSchema("split")).isEqualTo(targetSplit).doesNotContain("errorThreshold");
                assertThat(catalog.modelJSonSchema("tokenizer")).isNull();
                assertThat(catalog.componentModel("http").getArtifactId()).isEqualTo(request[3]);
                assertThat(catalog.componentModel("http").getVersion()).isEqualTo(request[4]);
                var result = tools.camel_configuration_validate("camel.component.http.max-total-connections=20",
                        request[0], request[1], request[2]);
                assertThat(result.camelVersion()).isEqualTo("4.8.0");
                assertThat(result.summary().valid()).isEqualTo(1);
            }
            CamelCatalog current = service.loadCatalog("main", BUILTIN_VERSION, null);
            assertThat(current.getCatalogVersion()).isEqualTo(BUILTIN_VERSION);
            assertThat(current.modelJSonSchema("split")).contains("errorThreshold");
            assertThat(current.modelJSonSchema("tokenizer")).isNotNull();
        }
    }
}
