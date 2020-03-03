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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class StringTemplateTest extends CamelTestSupport {
    
    @Test
    public void test() throws Exception {
        Exchange response = template.request("direct:a", exchange -> {
            exchange.getIn().setBody("Monday");
            exchange.getIn().setHeader("name", "Christian");
            exchange.setProperty("item", "7");
        });
        
        assertEquals("Dear Christian. You ordered item 7 on Monday.", response.getMessage().getBody());
        assertEquals("org/apache/camel/component/stringtemplate/template.tm", response.getMessage().getHeader(StringTemplateConstants.STRINGTEMPLATE_RESOURCE_URI));
        assertEquals("Christian", response.getMessage().getHeader("name"));
    }
    
    @Test
    public void testVelocityContext() throws Exception {
        Exchange exchange = template.request("direct:a", exchange1 -> {
            exchange1.getIn().setBody("");
            exchange1.getIn().setHeader("name", "Christian");
            Map<String, Object> variableMap = new HashMap<>();
            Map<String, Object> headersMap = new HashMap<>();
            headersMap.put("name", "Willem");
            variableMap.put("headers", headersMap);
            variableMap.put("body", "Monday");
            variableMap.put("exchange", exchange1);
            exchange1.getIn().setHeader(StringTemplateConstants.STRINGTEMPLATE_VARIABLE_MAP, variableMap);
            exchange1.setProperty("item", "7");
        });

        assertEquals("Dear Willem. You ordered item 7 on Monday.", exchange.getMessage().getBody());
        assertEquals("Christian", exchange.getMessage().getHeader("name"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:a").
                        to("string-template:org/apache/camel/component/stringtemplate/template.tm");
                // END SNIPPET: example
            }
        };
    }
}