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
package org.apache.camel.component.stringtemplate;

import java.util.HashMap;
import java.util.Map;

import javax.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class StringTemplateTest extends CamelTestSupport {
    
    @Test
    public void test() throws Exception {
        final DataHandler dataHandler = new DataHandler("my attachment", "text/plain");
        
        Exchange response = template.request("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().addAttachment("item", dataHandler);
                exchange.getIn().setBody("Monday");
                exchange.getIn().setHeader("name", "Christian");
                exchange.setProperty("item", "7");
            }
        });
        
        assertEquals("Dear Christian. You ordered item 7 on Monday.", response.getOut().getBody());
        assertEquals("org/apache/camel/component/stringtemplate/template.tm", response.getOut().getHeader(StringTemplateConstants.STRINGTEMPLATE_RESOURCE_URI));
        assertEquals("Christian", response.getOut().getHeader("name"));
        assertSame(dataHandler, response.getOut().getAttachment("item"));
    }
    
    @Test
    public void testVelocityContext() throws Exception {
        Exchange exchange = template.request("direct:a", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("");
                exchange.getIn().setHeader("name", "Christian");
                Map<String, Object> variableMap = new HashMap<String, Object>();
                Map<String, Object> headersMap = new HashMap<String, Object>();
                headersMap.put("name", "Willem");
                variableMap.put("headers", headersMap);
                variableMap.put("body", "Monday");
                variableMap.put("exchange", exchange);
                exchange.getIn().setHeader(StringTemplateConstants.STRINGTEMPLATE_VARIABLE_MAP, variableMap);
                exchange.setProperty("item", "7");
            }
        });

        assertEquals("Dear Willem. You ordered item 7 on Monday.", exchange.getOut().getBody());
        assertEquals("Christian", exchange.getOut().getHeader("name"));
    }

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