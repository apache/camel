/**
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
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.model.ModelHelper;

public class DumpModelAsXmlPlaceholdersTest extends ContextTestSupport {

    public void testDumpModelAsXml() throws Exception {
        assertEquals("Gouda", context.getRoutes().get(0).getId());
        String xml = ModelHelper.dumpModelAsXml(context, context.getRouteDefinition("Gouda"));
        assertNotNull(xml);
        log.info(xml);
        assertTrue(xml.contains("<route xmlns=\"http://camel.apache.org/schema/spring\" customId=\"true\" id=\"Gouda\">"));
        assertTrue(xml.contains("<from uri=\"direct:start-{{cheese.type}}\"/>"));
        assertTrue(xml.contains("<to customId=\"true\" id=\"log\" uri=\"direct:end-{{cheese.type}}\"/>"));
    }

    @Override

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start-{{cheese.type}}").routeId("{{cheese.type}}")
                        .to("direct:end-{{cheese.type}}").id("log");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        PropertiesComponent component = new PropertiesComponent();
        component.setLocation("classpath:org/apache/camel/component/properties/cheese.properties");
        context.addComponent("properties", component);
        return context;
    }

}
