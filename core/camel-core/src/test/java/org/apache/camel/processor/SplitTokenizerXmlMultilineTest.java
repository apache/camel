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
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class SplitTokenizerXmlMultilineTest extends ContextTestSupport {

    @Test
    public void testSingleLine() throws Exception {
        String payload = "<Parent>\n" + "\t<Child A=\"1\" B=\"2\"/>\n" + "</Parent>";

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", payload);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMultipleLines() throws Exception {
        String payload = "<Parent>\n" + "\t<Child A=\"1\"\n" + "\tB=\"2\"/>\n" + "</Parent>";

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", payload);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").split().tokenizeXML("Child").to("mock:result");
            }
        };
    }
}
