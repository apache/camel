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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.builder.Namespaces;
import org.junit.Test;

public class SplitterWithXqureyTest extends ContextTestSupport {
    private static String xmlData = "<workflow id=\"12345\" xmlns=\"http://camel.apache.org/schema/one\" " + "xmlns:two=\"http://camel.apache.org/schema/two\">"
                                    + "<person><name>Willem</name></person> " + "<other><two:test>One</two:test></other>" + "<other><two:test>Two</two:test></other>"
                                    + "<other><test>Three</test></other>" + "<other><test>Foure</test></other></workflow>";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // split the message with namespaces defined
                Namespaces namespaces = new Namespaces("one", "http://camel.apache.org/schema/one");
                from("direct:endpoint").split().xpath("//one:other", namespaces).to("mock:result");
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
        for (Exchange exchange : result.getExchanges()) {
            String message = exchange.getIn().getBody(String.class);
            log.debug("The message is " + message);
            assertTrue("The splitted message should start with <other", message.indexOf("<other") == 0);
        }

    }

}
