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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogDevConsoleTest extends CamelCommandBaseTestSupport {

    @Test
    void shouldListDevConsolesFromCatalog() throws Exception {
        CatalogDevConsole command = createCommand();

        int exit = command.doCall();

        assertEquals(0, exit);
        assertTrue(printer.getOutput().contains("context"),
                "default listing should include the context dev-console, was: " + printer.getOutput());
    }

    @Test
    void shouldNarrowToFilteredDevConsole() throws Exception {
        CatalogDevConsole command = createCommand();
        command.filterName = "context";

        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("context"), "filtered listing should include context, was: " + out);
        assertFalse(out.contains("gc"), "filtered listing should exclude unrelated dev-consoles, was: " + out);
    }

    private CatalogDevConsole createCommand() {
        CatalogDevConsole command = new CatalogDevConsole(new CamelJBangMain().withPrinter(printer));
        // options are normally defaulted by picocli; set them as we construct the command directly
        command.sort = "name";
        command.mavenResolver = new MavenResolverMixin();
        return command;
    }
}
