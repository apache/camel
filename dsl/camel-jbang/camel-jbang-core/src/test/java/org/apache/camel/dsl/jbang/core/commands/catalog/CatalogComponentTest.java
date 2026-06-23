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
package org.apache.camel.dsl.jbang.core.commands.catalog;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.MavenResolverMixin;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogComponentTest extends CamelCommandBaseTestSupport {

    @Test
    void shouldListComponentsFromCatalog() throws Exception {
        CatalogComponent command = createCommand();

        int exit = command.doCall();

        assertEquals(0, exit);
        assertTrue(printer.getOutput().contains("kafka"),
                "default listing should include the kafka component, was: " + printer.getOutput());
    }

    @Test
    void shouldNarrowToFilteredComponent() throws Exception {
        CatalogComponent command = createCommand();
        command.filterName = "kafka";

        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("kafka"), "filtered listing should include kafka, was: " + out);
        assertFalse(out.contains("timer"), "filtered listing should exclude unrelated components, was: " + out);
    }

    @Test
    void shouldRenderJsonOutput() throws Exception {
        CatalogComponent command = createCommand();
        command.filterName = "kafka";
        command.jsonOutput = true;

        int exit = command.doCall();

        assertEquals(0, exit);
        JsonArray rows = (JsonArray) Jsoner.deserialize(printer.getOutput());
        assertTrue(rows.stream()
                .map(JsonObject.class::cast)
                .anyMatch(row -> "kafka".equals(row.getString("name"))),
                "JSON output should contain a row with name kafka, was: " + printer.getOutput());
    }

    @Test
    void shouldSuggestSimilarWhenFilterHasNoMatch() throws Exception {
        CatalogComponent command = createCommand();
        command.filterName = "kafkaa";

        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("No results for filter: kafkaa"),
                "should report no results for an unknown filter, was: " + out);
        assertTrue(out.contains("camel doc kafkaa"),
                "should suggest the doc command for the filter, was: " + out);
    }

    private CatalogComponent createCommand() {
        CatalogComponent command = new CatalogComponent(new CamelJBangMain().withPrinter(printer));
        // options are normally defaulted by picocli; set them as we construct the command directly
        command.sort = "name";
        command.mavenResolver = new MavenResolverMixin();
        return command;
    }
}
