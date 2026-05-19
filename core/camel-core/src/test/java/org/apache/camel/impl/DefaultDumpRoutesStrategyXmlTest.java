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
package org.apache.camel.impl;

import java.io.File;
import java.io.FileInputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultDumpRoutesStrategyXmlTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setDumpRoutes("xml");

        DefaultDumpRoutesStrategy drd = new DefaultDumpRoutesStrategy();
        drd.setInclude("routes,routeTemplates");
        drd.setLog(false);
        drd.setOutput(testDirectory().toString());
        drd.setResolvePlaceholders(false);
        context.addService(drd);

        return context;
    }

    @Test
    public void testDumpXmlHasCorrectStructure() throws Exception {
        File dir = testDirectory().toFile();
        File[] files = dir.listFiles();
        assertThat(files).as("Expected dump files in %s", dir).isNotNull().isNotEmpty();

        StringBuilder all = new StringBuilder();
        for (File f : files) {
            if (f.getName().endsWith(".xml")) {
                all.append(IOHelper.loadText(new FileInputStream(f)));
            }
        }
        String xml = all.toString();

        assertThat(xml)
                .as("XML dump structure check.\nActual xml:\n%s", xml)
                // the dump wraps output in <camel> and strips the outer container elements
                .contains("<camel>", "</camel>")
                // outer container tags must be stripped entirely (both opening and closing) - CAMEL-23521
                .doesNotContain("<routes", "<routeTemplates")
                // individual route elements must be present and properly closed
                .contains("<route ", "</route>", "<routeTemplate ", "</routeTemplate>");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("greeting")
                        .from("direct:template")
                        .setBody().simple("{{greeting}} ${body}");

                from("direct:greet").routeId("greet")
                        .setBody().constant("Hello World");
            }
        };
    }
}
