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
package org.apache.camel.component.properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SendDefinition;

/**
 *
 */
public class PropertiesRouteFromTest extends ContextTestSupport {

    public void testPropertiesRouteFrom() throws Exception {
        ProcessorDefinition out = context.getRouteDefinition("foo").getOutputs().get(0);
        assertEquals("{{cool.end}}", ((SendDefinition) out).getUri());

        String uri = context.getRouteDefinition("foo").getInputs().get(0).getUri();
        assertEquals("{{cool.start}}", uri);

        // use a routes definition to dump the routes
        String xml = ModelHelper.dumpModelAsXml(context, context.getRouteDefinition("foo"));
        assertTrue(xml.contains("<from uri=\"{{cool.start}}\"/>"));
        assertTrue(xml.contains("<to id=\"to1\" uri=\"{{cool.end}}\"/>"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("{{cool.start}}").routeId("foo")
                    .to("{{cool.end}}");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("properties", new PropertiesComponent("classpath:org/apache/camel/component/properties/myproperties.properties"));
        return context;
    }

}
