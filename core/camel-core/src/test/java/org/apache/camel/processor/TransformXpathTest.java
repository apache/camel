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

import java.io.File;

import org.w3c.dom.NodeList;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Based on user forum trouble
 */
public class TransformXpathTest extends ContextTestSupport {

    @Test
    public void testTransformWithXpath() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(NodeList.class);

        String xml = context.getTypeConverter().convertTo(String.class, new File("src/test/resources/org/apache/camel/processor/students.xml"));

        template.sendBody("direct:start", xml);

        assertMockEndpointsSatisfied();

        NodeList list = mock.getReceivedExchanges().get(0).getIn().getBody(NodeList.class);
        assertEquals(2, list.getLength());

        assertEquals("Claus", context.getTypeConverter().convertTo(String.class, list.item(0).getTextContent().trim()));
        assertEquals("Hadrian", context.getTypeConverter().convertTo(String.class, list.item(1).getTextContent().trim()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").transform().xpath("//students/student").to("mock:result");
            }
        };
    }
}
