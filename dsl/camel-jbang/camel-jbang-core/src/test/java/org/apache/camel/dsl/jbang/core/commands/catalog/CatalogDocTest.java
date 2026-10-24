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
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CatalogDocTest extends CamelCommandBaseTestSupport {

    @Test
    public void shouldDocumentAComponentWithoutTheKameletCatalog() throws Exception {
        CatalogDoc command = createCommand("timer", false);
        // a Kamelet catalog that cannot be downloaded: the component is found before it is needed
        command.kameletsVersion = "0.0.0-no-such-version";

        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Assertions.assertTrue(printer.getOutput().contains("timer"), "Should document the timer component");
    }

    @Test
    public void shouldPrintExampleForConsumerComponent() throws Exception {
        CatalogDoc command = createCommand("timer", true);

        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("from:"), "Should have a from clause");
        Assertions.assertTrue(output.contains("timer"), "Should reference the timer component");
        Assertions.assertTrue(output.contains("log:"), "Consumer components should route to log");
    }

    @Test
    public void shouldPrintExampleForProducerOnlyComponent() throws Exception {
        CatalogDoc command = createCommand("log", true);

        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("timer:tick"), "Producer-only should use timer as source");
        Assertions.assertTrue(output.contains("log"), "Should reference the log component");
    }

    @Test
    public void shouldPrintExampleForKafka() throws Exception {
        CatalogDoc command = createCommand("kafka", true);

        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("kafka:"), "Should contain kafka URI");
        Assertions.assertTrue(output.contains("route:"), "Should have route structure");
        Assertions.assertTrue(output.contains("camel run"), "Should show run instructions");
    }

    @Test
    public void shouldPrintStandardDocWithoutExampleFlag() throws Exception {
        CatalogDoc command = createCommand("timer", false);

        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Component Name: timer"), "Should show standard doc");
        Assertions.assertFalse(output.contains("camel run my-route.yaml"), "Should not show example run hint");
    }

    @Test
    public void shouldReportUnknownComponentAsNotFound() throws Exception {
        CatalogDoc command = createCommand("component:no-such-component-xyz", false);

        CliException e = Assertions.assertThrows(CliException.class, command::doCall);

        Assertions.assertEquals(CliExitCode.NOT_FOUND, e.getExitCode());
        Assertions.assertTrue(e.getMessage().startsWith("Camel component: no-such-component-xyz not found"),
                e.getMessage());
        Assertions.assertEquals("", printer.getOutput());
    }

    @Test
    public void shouldPrintComponentDocAsJson() throws Exception {
        CatalogDoc command = createCommand("timer", false);
        command.jsonOutput = true;

        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        JsonObject doc = Jsoner.deserialize(printer.getOutput(), (JsonObject) null);
        Assertions.assertNotNull(doc, printer.getOutput());
        Assertions.assertEquals("component", doc.getString("kind"));
        Assertions.assertEquals("timer", doc.getString("name"));
    }

    @Test
    public void shouldDocumentAnEip() throws Exception {
        CatalogDoc command = createCommand("split", false);

        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.startsWith("EIP Name: split"), output);
        Assertions.assertTrue(output.contains("The split EIP supports (total: "), output);
        Assertions.assertTrue(output.contains("aggregationStrategy"), output);
        Assertions.assertTrue(output.contains("/eips/split-eip.html"), output);
    }

    @Test
    public void shouldDocumentTheEipWithTheNameOfAComponentWithThePrefix() throws Exception {
        CatalogDoc command = createCommand("log", false);
        Assertions.assertEquals(0, command.doCall());
        Assertions.assertTrue(printer.getOutput().startsWith("Component Name: log"), printer.getOutput());

        printer = new StringPrinter();
        command = createCommand("eip:log", false);
        Assertions.assertEquals(0, command.doCall());
        Assertions.assertTrue(printer.getOutput().startsWith("EIP Name: log"), printer.getOutput());
    }

    @Test
    public void shouldPrintTheLinkOfAnEip() throws Exception {
        CatalogDoc command = createCommand("split", false);
        command.url = true;

        Assertions.assertEquals(0, command.doCall());

        String output = printer.getOutput();
        Assertions.assertTrue(output.matches("https://camel\\.apache\\.org/components/[^/]+/eips/split-eip\\.html"),
                output);
    }

    @Test
    public void shouldSuggestASimilarEip() throws Exception {
        CatalogDoc command = createCommand("eip:spllit", false);

        CliException e = Assertions.assertThrows(CliException.class, command::doCall);

        Assertions.assertEquals(CliExitCode.NOT_FOUND, e.getExitCode());
        Assertions.assertTrue(e.getMessage().startsWith("Camel eip: spllit not found. Did you mean? split"),
                e.getMessage());
    }

    @Test
    public void shouldPrintEipDocAsJson() throws Exception {
        CatalogDoc command = createCommand("split", false);
        command.jsonOutput = true;

        Assertions.assertEquals(0, command.doCall());

        JsonObject doc = Jsoner.deserialize(printer.getOutput(), (JsonObject) null);
        Assertions.assertNotNull(doc, printer.getOutput());
        Assertions.assertEquals("eip", doc.getString("kind"));
    }

    @Test
    public void shouldDocumentADataFormatAndALanguage() throws Exception {
        CatalogDoc command = createCommand("jackson", false);
        Assertions.assertEquals(0, command.doCall());
        Assertions.assertTrue(printer.getOutput().startsWith("Dataformat Name: jackson"), printer.getOutput());
        Assertions.assertTrue(printer.getOutput().contains("<artifactId>camel-jackson</artifactId>"),
                printer.getOutput());

        printer = new StringPrinter();
        command = createCommand("language:simple", false);
        Assertions.assertEquals(0, command.doCall());
        Assertions.assertTrue(printer.getOutput().startsWith("Language Name: simple"), printer.getOutput());
    }

    @Test
    public void shouldPrintTheMessageBody() throws Exception {
        // CAMEL-24961: the body at each side, as the documentation page shows it
        CatalogDoc command = createCommand("file", false);
        Assertions.assertEquals(0, command.doCall());
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Message body (with the default options"), output);
        Assertions.assertTrue(output.contains("Producer input"), output);
        Assertions.assertTrue(output.contains("text, bytes, stream or file"), output);

        printer = new StringPrinter();
        command = createCommand("jackson", false);
        Assertions.assertEquals(0, command.doCall());
        Assertions.assertTrue(printer.getOutput().contains("Unmarshal output"), printer.getOutput());

        // a component whose body types are not in the catalog has no such table
        printer = new StringPrinter();
        command = createCommand("weather", false);
        Assertions.assertEquals(0, command.doCall());
        Assertions.assertFalse(printer.getOutput().contains("Message body"), printer.getOutput());
    }

    @Test
    public void shouldFilterTheOptions() throws Exception {
        CatalogDoc command = createCommand("timer", false);
        command.filter = "delay";

        Assertions.assertEquals(0, command.doCall());

        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("match-filter: "), output);
        Assertions.assertTrue(output.contains("delay"), output);
        Assertions.assertFalse(output.contains("repeatCount"), output);
    }

    @Test
    public void shouldDocumentCamelMain() throws Exception {
        CatalogDoc command = createCommand("main", false);

        Assertions.assertEquals(0, command.doCall());

        Assertions.assertTrue(printer.getOutput().startsWith("Name: main"), printer.getOutput());
    }

    private CatalogDoc createCommand(String name, boolean example) {
        CatalogDoc command = new CatalogDoc(new CamelJBangMain().withPrinter(printer));
        command.name = name;
        command.example = example;
        command.mavenResolver = new MavenResolverMixin();
        return command;
    }
}
