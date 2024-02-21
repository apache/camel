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
package org.apache.camel.component.stringtemplate;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringTemplateViaHeaderTest extends CamelTestSupport {

    @Test
    public void testByHeaderTemplate() {
        Exchange response = template.request("direct:b", exchange -> {
            exchange.getIn().setHeader("name", "Sheldon");
            exchange.getIn().setHeader(StringTemplateConstants.STRINGTEMPLATE_TEMPLATE,
                    "Hi <headers.name>.");
        });

        assertEquals("Hi Sheldon.", response.getMessage().getBody());
        assertEquals("dummy",
                response.getMessage().getHeader(StringTemplateConstants.STRINGTEMPLATE_RESOURCE_URI));
        assertEquals("Sheldon", response.getMessage().getHeader("name"));
    }

    @Test
    public void testByHeaderUri() {
        Exchange response = template.request("direct:a", exchange -> {
            exchange.getIn().setBody("Monday");
            exchange.getIn().setHeader("name", "Christian");
            exchange.getIn().setHeader(StringTemplateConstants.STRINGTEMPLATE_RESOURCE_URI,
                    "org/apache/camel/component/stringtemplate/template.tm");
            exchange.setProperty("item", "7");
        });

        assertEquals("Dear Christian. You ordered item 7 on Monday.", response.getMessage().getBody());
        assertEquals("org/apache/camel/component/stringtemplate/template.tm",
                response.getMessage().getHeader(StringTemplateConstants.STRINGTEMPLATE_RESOURCE_URI));
        assertEquals("Christian", response.getMessage().getHeader("name"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").to(
                        "string-template:dummy?allowTemplateFromHeader=true&allowContextMapAll=true");
                from("direct:b").to(
                        "string-template:dummy?allowTemplateFromHeader=true");
            }
        };
    }
}
