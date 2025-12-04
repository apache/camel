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

package org.apache.camel.component.properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PropertiesComponentResolvedValueTest extends ContextTestSupport {

    @Test
    public void testResolved() {
        org.apache.camel.spi.PropertiesComponent pc = context.getPropertiesComponent();

        Assertions.assertTrue(pc.getResolvedValue("unknown").isEmpty());
        Assertions.assertTrue(pc.getResolvedValue("greeting").isPresent());
        Assertions.assertTrue(pc.getResolvedValue("cool.end").isPresent());
        Assertions.assertTrue(pc.getResolvedValue("place").isPresent());
        Assertions.assertTrue(pc.getResolvedValue("myserver").isPresent());

        // added initial via code
        var p = pc.getResolvedValue("greeting").get();
        Assertions.assertEquals("greeting", p.name());
        Assertions.assertEquals("Hello World", p.originalValue());
        Assertions.assertEquals("Hello World", p.value());
        Assertions.assertEquals("Hi", p.defaultValue());
        Assertions.assertEquals("InitialProperties", p.source());

        // from properties file
        p = pc.getResolvedValue("cool.end").get();
        Assertions.assertEquals("cool.end", p.name());
        Assertions.assertEquals("mock:result", p.originalValue());
        Assertions.assertEquals("mock:result", p.value());
        Assertions.assertNull(p.defaultValue());
        Assertions.assertEquals("classpath:org/apache/camel/component/properties/myproperties.properties", p.source());

        // no source but using default value
        p = pc.getResolvedValue("place").get();
        Assertions.assertEquals("place", p.name());
        Assertions.assertEquals("Paris", p.originalValue());
        Assertions.assertEquals("Paris", p.value());
        Assertions.assertEquals("Paris", p.defaultValue());
        Assertions.assertNull(p.source());

        // nested
        p = pc.getResolvedValue("myserver").get();
        Assertions.assertEquals("myserver", p.name());
        Assertions.assertEquals("127.0.0.1", p.value());
        Assertions.assertEquals("{{env:MY_SERVER:127.0.0.1}}", p.originalValue());
        Assertions.assertNull(p.defaultValue());
        Assertions.assertEquals("env", p.source());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setBody(constant("{{greeting:Hi}}"))
                        .setHeader("bar", constant("{{?place:Paris}}"))
                        .setHeader("server", constant("{{myserver}}"))
                        .to("{{cool.end}}");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent()
                .setLocation("classpath:org/apache/camel/component/properties/myproperties.properties");
        context.getPropertiesComponent().addInitialProperty("greeting", "Hello World");
        context.getPropertiesComponent().addInitialProperty("myserver", "{{env:MY_SERVER:127.0.0.1}}");
        return context;
    }
}
