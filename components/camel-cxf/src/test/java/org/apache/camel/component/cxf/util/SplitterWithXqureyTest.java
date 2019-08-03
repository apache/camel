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
package org.apache.camel.component.cxf.util;

import org.w3c.dom.Element;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SplitterWithXqureyTest extends CamelTestSupport {

    private static String xmlData = "<workflow id=\"12345\" xmlns=\"http://camel.apache.org/schema/one\" "
        + "xmlns:two=\"http://camel.apache.org/schema/two\">"
        + "<person><name>Willem</name></person> "
        + "<other><two:test name=\"123\">One</two:test></other>"
        + "<other><two:test name=\"456\">Two</two:test></other>"
        + "<other><test>Three</test></other>"
        + "<other><test>Foure</test></other></workflow>";
    private static String[] verifyStrings = new String[] {
        "<other xmlns=\"http://camel.apache.org/schema/one\" xmlns:two=\"http://camel.apache.org/schema/two\"><two:test name=\"123\">One</two:test></other>",
        "<other xmlns=\"http://camel.apache.org/schema/one\" xmlns:two=\"http://camel.apache.org/schema/two\"><two:test name=\"456\">Two</two:test></other>",
        "<other xmlns=\"http://camel.apache.org/schema/one\"><test>Three</test></other>",
        "<other xmlns=\"http://camel.apache.org/schema/one\"><test>Foure</test></other>"
    };

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // split the message with namespaces defined 
                Namespaces namespaces = new Namespaces("one", "http://camel.apache.org/schema/one");                
                from("direct:endpoint").split().xpath("//one:other", namespaces).to("mock:result");
                
                from("direct:toString").split().xpath("//one:other", namespaces)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            Element element = (Element) exchange.getIn().getBody();
                            String message = CxfUtilsTestHelper.elementToString(element);
                            exchange.getOut().setBody(message);
                        }
                    })
                    .to("mock:result");
            }
        };
    }
    
    @Test
    public void testSenderXmlData() throws Exception {        
        MockEndpoint result = getMockEndpoint("mock:result");
        result.reset();
        result.expectedMessageCount(4);
        template.sendBody("direct:endpoint", xmlData);
        assertMockEndpointsSatisfied();
        int i = 0;
        for (Exchange exchange : result.getExchanges()) {
            Element element = (Element) exchange.getIn().getBody();           
            String message = CxfUtilsTestHelper.elementToString(element);
            log.info("The splited message is " + message);
            assertTrue("The splitted message should start with <other", message.indexOf("<other") == 0);
            assertEquals("Get a wrong message", verifyStrings[i], message);
            i++;
        }
    }
    
    @Test
    public void testToStringProcessor() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.reset();
        result.expectedMessageCount(4);
        template.sendBody("direct:toString", xmlData);
        assertMockEndpointsSatisfied();
        int i = 0;
        for (Exchange exchange : result.getExchanges()) {
            String message = exchange.getIn().getBody(String.class);
            assertEquals("Get a wrong message", verifyStrings[i], message);
            i++;
        }
    }

}
