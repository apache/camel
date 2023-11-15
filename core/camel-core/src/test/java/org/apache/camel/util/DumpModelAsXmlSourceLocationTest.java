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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DumpModelAsXmlSourceLocationTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setDebugging(true);
        return context;
    }

    @Test
    public void testDumpModelAsXml() throws Exception {
        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("myRoute"));
        assertNotNull(xml);
        log.info(xml);

        Assertions.assertTrue(xml.contains(
                "sourceLineNumber=\"66\" sourceLocation=\"DumpModelAsXmlSourceLocationTest.java\""));
    }

    @Test
    public void testDumpModelAsXmlExternal() throws Exception {
        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("cool"));
        assertNotNull(xml);
        log.info(xml);

        Assertions.assertTrue(xml.contains(
                "sourceLineNumber=\"25\" sourceLocation=\"MyCoolRoute.java\" uri=\"direct:cool\"/>"));
        Assertions.assertTrue(xml.contains("sourceLineNumber=\"26\" sourceLocation=\"MyCoolRoute.java\""));
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {
                new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from("direct:start").routeId("myRoute")
                                .filter(simple("${body} > 10"))
                                .to("mock:result");
                    }
                },
                new MyCoolRoute()
        };
    }

}
