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
import org.junit.Test;

/**
 * Test that placeholder DSL is working as expected.
 */
public class OptionalPropertiesDslInvalidSyntaxTest extends ContextTestSupport {

    @Test
    public void testPlaceholderDslKeyNotFoundTest() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").multicast().stopOnException("{{xxx}}").to("mock:a").throwException(new IllegalAccessException("Damn")).to("mock:b");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (Exception e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Property with key [xxx] not found in properties from text: {{xxx}}", cause.getMessage());
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("classpath:org/apache/camel/component/properties/myproperties.properties");
        return context;
    }

}
