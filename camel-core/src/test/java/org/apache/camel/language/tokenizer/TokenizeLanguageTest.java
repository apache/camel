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
package org.apache.camel.language.tokenizer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

public class TokenizeLanguageTest extends ContextTestSupport {

    public void testSendClosedTagMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("<child some_attr='a' anotherAttr='a'></child>", "<child some_attr='b' anotherAttr='b'></child>");

        template.sendBody("direct:start",
            "<?xml version='1.0' encoding='UTF-8'?><parent><child some_attr='a' anotherAttr='a'></child><child some_attr='b' anotherAttr='b'></child></parent>");

        assertMockEndpointsSatisfied();
    }

    public void testSendClosedTagWithLineBreaksMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("<child some_attr='a' anotherAttr='a'>\n</child>", "<child some_attr='b' anotherAttr='b'>\n</child>");

        template.sendBody("direct:start",
            "<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<parent>\n"
                + "<child some_attr='a' anotherAttr='a'>\n"
                + "</child>\n"
                + "<child some_attr='b' anotherAttr='b'>\n"
                + "</child>\n"
                + "</parent>");

        assertMockEndpointsSatisfied();
    }

    public void xxxTestSendSelfClosingTagMessageToTokenize() throws Exception {
        // TODO: ignored test as it does not work, there is a CAMEL ticket about this
        getMockEndpoint("mock:result").expectedBodiesReceived("<child some_attr='a' anotherAttr='a' />", "<child some_attr='b' anotherAttr='b' />");

        template.sendBody("direct:start",
            "<?xml version='1.0' encoding='UTF-8'?><parent><child some_attr='a' anotherAttr='a' /><child some_attr='b' anotherAttr='b' /></parent>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .split().tokenizeXML("child")
                        .to("mock:result")
                    .end();
            }
        };
    }
}
