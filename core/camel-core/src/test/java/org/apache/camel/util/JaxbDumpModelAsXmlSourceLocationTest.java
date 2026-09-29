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
import org.apache.camel.xml.jaxb.JaxbModelToXMLDumper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The JAXB based dumper includes the source location when asked for, also when not debugging.
 */
public class JaxbDumpModelAsXmlSourceLocationTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setSourceLocationEnabled(true);
        context.getPropertiesComponent().addInitialProperty("myUri", "mock:result");
        return context;
    }

    @Test
    public void testDumpModelAsXml() throws Exception {
        String xml = new JaxbModelToXMLDumper().dumpModelAsXml(context,
                context.getRouteDefinition("myRoute"), false, false, true);
        assertNotNull(xml);
        log.info(xml);

        Assertions.assertTrue(xml.contains("sourceLocation=\"JaxbDumpModelAsXmlSourceLocationTest.java\""), xml);
    }

    @Test
    public void testDumpModelAsXmlResolvePlaceholders() throws Exception {
        String xml = new JaxbModelToXMLDumper().dumpModelAsXml(context,
                context.getRouteDefinition("myRoute"), true, false, true);
        assertNotNull(xml);
        log.info(xml);

        Assertions.assertTrue(xml.contains("uri=\"mock:result\""), xml);
        Assertions.assertTrue(xml.contains("sourceLocation=\"JaxbDumpModelAsXmlSourceLocationTest.java\""), xml);
    }

    @Test
    public void testDumpModelAsXmlNoSourceLocation() throws Exception {
        String xml = new JaxbModelToXMLDumper().dumpModelAsXml(context,
                context.getRouteDefinition("myRoute"), true, false, false);
        assertNotNull(xml);
        log.info(xml);

        Assertions.assertFalse(xml.contains("sourceLocation="), xml);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute")
                        .filter(simple("${body} > 10"))
                        .to("{{myUri}}");
            }
        };
    }

}
