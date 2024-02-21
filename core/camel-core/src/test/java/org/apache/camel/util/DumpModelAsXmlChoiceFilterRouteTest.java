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
package org.apache.camel.util;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class DumpModelAsXmlChoiceFilterRouteTest extends ContextTestSupport {

    @Test
    public void testDumpModelAsXmlNoEmptyLines() throws Exception {
        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("myRoute"));
        assertNotNull(xml);
        log.info(xml);

        // should not contain empty lines
        for (String line : xml.split("\n")) {
            line = line.trim();
            assertFalse(line.isEmpty(), "Should not contain empty lines");
        }
    }

    @Test
    public void testDumpModelAsXml() throws Exception {
        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("myRoute"));
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("<header>dude</header>"));
        assertTrue(xml.contains("<header>gold</header>"));
        assertTrue(xml.contains("<header>extra-gold</header>"));
        assertTrue(xml.contains("<simple>${body} contains 'Camel'</simple>"));
    }

    @Test
    public void testDumpModelAsXmAl() throws Exception {
        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("a"));
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("<constant>bar</constant>"));
        assertTrue(xml.contains("<expressionDefinition>header{test} is not null</expressionDefinition>"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("myRoute").to("log:input").transform().header("dude").choice().when()
                        .header("gold").to("mock:gold").filter().header("extra-gold")
                        .to("mock:extra-gold").endChoice().when().simple("${body} contains 'Camel'").to("mock:camel")
                        .otherwise().to("mock:other").end().to("mock:result");

                from("seda:a").routeId("a").setProperty("foo").constant("bar").choice().when(header("test").isNotNull())
                        .log("not null").when(xpath("/foo/bar")).log("xpath").end()
                        .to("mock:a");
            }
        };
    }
}
