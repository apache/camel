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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertNotNull(files, "Expected dump files in " + dir);

        StringBuilder all = new StringBuilder();
        for (File f : files) {
            if (f.getName().endsWith(".xml")) {
                all.append(IOHelper.loadText(new FileInputStream(f)));
            }
        }
        String xml = all.toString();

        // the dump wraps output in <camel> and strips the outer container elements
        assertTrue(xml.contains("<camel>"), "Expected <camel> root wrapper");
        assertTrue(xml.contains("</camel>"), "Expected </camel> root wrapper closing tag");

        // outer container tags must be stripped entirely (both opening and closing) - CAMEL-23521
        assertFalse(xml.contains("<routes"), "Outer <routes> wrapper must be stripped");
        assertFalse(xml.contains("<routeTemplates"), "Outer <routeTemplates> wrapper must be stripped");

        // individual route elements must be present and properly closed
        assertTrue(xml.contains("<route "), "Expected individual <route> elements");
        assertTrue(xml.contains("</route>"), "Expected </route> closing tag");
        assertTrue(xml.contains("<routeTemplate "), "Expected individual <routeTemplate> elements");
        assertTrue(xml.contains("</routeTemplate>"), "Expected </routeTemplate> closing tag");
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
