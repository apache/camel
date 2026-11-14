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

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogToolsTest {

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Runs only local - requires Maven Central access")
    void mixedRuntimeRequestsKeepTheirCoreVersionAndProviderCoordinates() {
        CatalogTools tools = new CatalogTools();
        String[][] requests = {
                { "spring-boot", "4.8.0", "4.8.0", "camel-http-starter", "4.8.0" },
                { "quarkus", "3.15.1", "4.8.0", "camel-quarkus-http", "3.15.0" },
                { "main", "4.8.0", "4.8.0", "camel-http", "4.8.0" },
                {
                        "main", null, new DefaultCamelCatalog().getCatalogVersion(), "camel-http",
                        new DefaultCamelCatalog().getCatalogVersion() }
        };
        for (int pass = 0; pass < 2; pass++) {
            for (String[] request : requests) {
                String scenario = "runtime=" + request[0] + ", version=" + request[1] + ", pass=" + pass;
                var components = tools.camel_catalog_components("http", null, 10, request[0], request[1]);
                assertThat(components.camelVersion()).as("%s: core version", scenario).isEqualTo(request[2]);
                var component = tools.camel_catalog_component_doc("http", request[0], request[1]);
                assertThat(component.artifactId()).as("%s: provider artifact", scenario).isEqualTo(request[3]);
                assertThat(component.version()).as("%s: provider version", scenario).isEqualTo(request[4]);
            }
        }
    }
}
