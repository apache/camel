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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CatalogDocTest extends CamelCommandBaseTestSupport {

    @Test
    public void shouldPrintExampleForConsumerComponent() throws Exception {
        CatalogDoc command = new CatalogDoc(new CamelJBangMain().withPrinter(printer));
        command.name = "timer";
        command.example = true;

        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("from:"), "Should have a from clause");
        Assertions.assertTrue(output.contains("timer"), "Should reference the timer component");
        Assertions.assertTrue(output.contains("log:"), "Consumer components should route to log");
    }

    @Test
    public void shouldPrintExampleForProducerOnlyComponent() throws Exception {
        CatalogDoc command = new CatalogDoc(new CamelJBangMain().withPrinter(printer));
        command.name = "log";
        command.example = true;

        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("timer:tick"), "Producer-only should use timer as source");
        Assertions.assertTrue(output.contains("log"), "Should reference the log component");
    }

    @Test
    public void shouldPrintExampleForKafka() throws Exception {
        CatalogDoc command = new CatalogDoc(new CamelJBangMain().withPrinter(printer));
        command.name = "kafka";
        command.example = true;

        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("kafka:"), "Should contain kafka URI");
        Assertions.assertTrue(output.contains("route:"), "Should have route structure");
        Assertions.assertTrue(output.contains("camel run"), "Should show run instructions");
    }

    @Test
    public void shouldPrintStandardDocWithoutExampleFlag() throws Exception {
        CatalogDoc command = new CatalogDoc(new CamelJBangMain().withPrinter(printer));
        command.name = "timer";

        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Component Name: timer"), "Should show standard doc");
        Assertions.assertFalse(output.contains("camel run my-route.yaml"), "Should not show example run hint");
    }
}
