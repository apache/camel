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
package org.apache.camel.language.tokenizer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.builder.Namespaces;
import org.junit.Test;

public class XMLTokenizeLanguageGroupingTest extends ContextTestSupport {

    @Test
    public void testSendClosedTagMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("<group><c:child some_attr='a' anotherAttr='a' xmlns:c=\"urn:c\"></c:child>"
                                                              + "<c:child some_attr='b' anotherAttr='b' xmlns:c=\"urn:c\"></c:child></group>");

        template
            .sendBody("direct:start",
                      "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child some_attr='a' anotherAttr='a'></c:child><c:child some_attr='b' anotherAttr='b'></c:child></c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendClosedTagWithLineBreaksMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("<group><c:child some_attr='a' anotherAttr='a' xmlns:c=\"urn:c\">\n</c:child>"
                                                              + "<c:child some_attr='b' anotherAttr='b' xmlns:c=\"urn:c\">\n</c:child></group>");

        template.sendBody("direct:start", "<?xml version='1.0' encoding='UTF-8'?>\n" + "<c:parent xmlns:c='urn:c'>\n" + "<c:child some_attr='a' anotherAttr='a'>\n" + "</c:child>\n"
                                          + "<c:child some_attr='b' anotherAttr='b'>\n" + "</c:child>\n" + "</c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendSelfClosingTagMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result")
            .expectedBodiesReceived("<group><c:child some_attr='a' anotherAttr='a'  xmlns:c=\"urn:c\"/>" + "<c:child some_attr='b' anotherAttr='b'  xmlns:c=\"urn:c\"/></group>");

        template
            .sendBody("direct:start",
                      "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child some_attr='a' anotherAttr='a' /><c:child some_attr='b' anotherAttr='b' /></c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendMixedClosingTagMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("<group><c:child some_attr='a' anotherAttr='a' xmlns:c=\"urn:c\">ha</c:child>"
                                                              + "<c:child some_attr='b' anotherAttr='b'  xmlns:c=\"urn:c\"/></group>",
                                                              "<group><c:child some_attr='c' xmlns:c=\"urn:c\"></c:child></group>");

        template.sendBody("direct:start", "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child some_attr='a' anotherAttr='a'>ha</c:child>"
                                          + "<c:child some_attr='b' anotherAttr='b' /><c:child some_attr='c'></c:child></c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendMixedClosingTagInsideMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result")
            .expectedBodiesReceived("<group><c:child name='child1' xmlns:c=\"urn:c\"><grandchild name='grandchild1'/> <grandchild name='grandchild2'/></c:child>"
                                    + "<c:child name='child2' xmlns:c=\"urn:c\"><grandchild name='grandchild1'></grandchild><grandchild name='grandchild2'></grandchild></c:child></group>");

        template.sendBody("direct:start", "<c:parent xmlns:c='urn:c'><c:child name='child1'><grandchild name='grandchild1'/> <grandchild name='grandchild2'/></c:child>"
                                          + "<c:child name='child2'><grandchild name='grandchild1'></grandchild><grandchild name='grandchild2'></grandchild></c:child></c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNamespacedChildMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result")
            .expectedBodiesReceived("<group><c:child xmlns:c='urn:c' some_attr='a' anotherAttr='a'></c:child><c:child xmlns:c='urn:c' some_attr='b' anotherAttr='b' /></group>");

        template.sendBody("direct:start", "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child xmlns:c='urn:c' some_attr='a' anotherAttr='a'></c:child>"
                                          + "<c:child xmlns:c='urn:c' some_attr='b' anotherAttr='b' /></c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNamespacedParentMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("<group><c:child some_attr='a' anotherAttr='a' xmlns:d=\"urn:d\" xmlns:c=\"urn:c\"></c:child>"
                                                              + "<c:child some_attr='b' anotherAttr='b' xmlns:d=\"urn:d\" xmlns:c=\"urn:c\"/></group>");

        template.sendBody("direct:start", "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\"><c:child some_attr='a' anotherAttr='a'></c:child>"
                                          + "<c:child some_attr='b' anotherAttr='b'/></c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendMoreParentsMessageToTokenize() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        if (getJavaMajorVersion() <= 7) {
            result.expectedBodiesReceived("<group><c:child some_attr='a' anotherAttr='a' xmlns:g=\"urn:g\" xmlns:d=\"urn:d\" xmlns:c=\"urn:c\"></c:child>"
                                          + "<c:child some_attr='b' anotherAttr='b' xmlns:g=\"urn:g\" xmlns:d=\"urn:d\" xmlns:c=\"urn:c\"/></group>");
        } else {
            result.expectedBodiesReceived("<group><c:child some_attr='a' anotherAttr='a' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"></c:child>"
                                          + "<c:child some_attr='b' anotherAttr='b' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"/></group>");
        }

        template
            .sendBody("direct:start",
                      "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                      + "<c:child some_attr='a' anotherAttr='a'></c:child><c:child some_attr='b' anotherAttr='b'/></c:parent></grandparent></g:greatgrandparent>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            Namespaces ns = new Namespaces("C", "urn:c");

            public void configure() {
                from("direct:start").split().xtokenize("//C:child", 'i', ns, 2).to("mock:result").end();
            }
        };
    }
}
