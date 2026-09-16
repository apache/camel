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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogToolsTest {

    private CatalogTools createTools() {
        CatalogService catalogService = new CatalogService();
        catalogService.catalogRepos = Optional.empty();

        CatalogTools tools = new CatalogTools();
        tools.catalogService = catalogService;
        return tools;
    }

    @Test
    void docsListsThePageNamesAndFilters() {
        CatalogTools tools = createTools();

        CatalogTools.DocListResult all = tools.camel_catalog_docs(null, 10, null);
        assertThat(all.returned()).isEqualTo(10);
        assertThat(all.total()).isGreaterThan(all.returned());

        CatalogTools.DocListResult kafka = tools.camel_catalog_docs("kafka", null, null);
        assertThat(kafka.names()).contains("kafka-component");
        assertThat(kafka.names()).allMatch(n -> n.contains("kafka"));
        assertThat(kafka.returned()).isEqualTo(kafka.total());
    }
}
