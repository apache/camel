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
import org.apache.camel.support.builder.Namespaces;
import org.junit.Test;

public class XMLTokenizeWrapLanguageGroupingTest extends ContextTestSupport {

    @Test
    public void testSendClosedTagMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child some_attr='a' anotherAttr='a'></c:child>"
                                                              + "<c:child some_attr='b' anotherAttr='b'></c:child></c:parent>");

        template
            .sendBody("direct:start",
                      "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child some_attr='a' anotherAttr='a'></c:child><c:child some_attr='b' anotherAttr='b'></c:child></c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendClosedTagWithLineBreaksMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result")
            .expectedBodiesReceived("<?xml version='1.0' encoding='UTF-8'?>\n<c:parent xmlns:c='urn:c'>\n<c:child some_attr='a' anotherAttr='a'>\n</c:child>"
                                    + "<c:child some_attr='b' anotherAttr='b'>\n</c:child></c:parent>");

        template.sendBody("direct:start", "<?xml version='1.0' encoding='UTF-8'?>\n" + "<c:parent xmlns:c='urn:c'>\n" + "<c:child some_attr='a' anotherAttr='a'>\n" + "</c:child>\n"
                                          + "<c:child some_attr='b' anotherAttr='b'>\n" + "</c:child>\n" + "</c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendSelfClosingTagMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child some_attr='a' anotherAttr='a' />"
                                                              + "<c:child some_attr='b' anotherAttr='b' /></c:parent>");

        template
            .sendBody("direct:start",
                      "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child some_attr='a' anotherAttr='a' /><c:child some_attr='b' anotherAttr='b' /></c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendMixedClosingTagMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child some_attr='a' anotherAttr='a'>ha</c:child>"
                                                              + "<c:child some_attr='b' anotherAttr='b' /></c:parent>",
                                                              "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child some_attr='c'></c:child></c:parent>");

        template.sendBody("direct:start", "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child some_attr='a' anotherAttr='a'>ha</c:child>"
                                          + "<c:child some_attr='b' anotherAttr='b' /><c:child some_attr='c'></c:child></c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendMixedClosingTagInsideMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result")
            .expectedBodiesReceived("<c:parent xmlns:c='urn:c'><c:child name='child1'><grandchild name='grandchild1'/> <grandchild name='grandchild2'/></c:child>"
                                    + "<c:child name='child2'><grandchild name='grandchild1'></grandchild><grandchild name='grandchild2'></grandchild></c:child></c:parent>");

        template.sendBody("direct:start", "<c:parent xmlns:c='urn:c'><c:child name='child1'><grandchild name='grandchild1'/> <grandchild name='grandchild2'/></c:child>"
                                          + "<c:child name='child2'><grandchild name='grandchild1'></grandchild><grandchild name='grandchild2'></grandchild></c:child></c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNamespacedChildMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result")
            .expectedBodiesReceived("<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child xmlns:c='urn:c' some_attr='a' anotherAttr='a'></c:child>"
                                    + "<c:child xmlns:c='urn:c' some_attr='b' anotherAttr='b' /></c:parent>");

        template.sendBody("direct:start", "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child xmlns:c='urn:c' some_attr='a' anotherAttr='a'></c:child>"
                                          + "<c:child xmlns:c='urn:c' some_attr='b' anotherAttr='b' /></c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNamespacedParentMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result")
            .expectedBodiesReceived("<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\"><c:child some_attr='a' anotherAttr='a'></c:child>"
                                    + "<c:child some_attr='b' anotherAttr='b'/></c:parent>");

        template
            .sendBody("direct:start",
                      "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\"><c:child some_attr='a' anotherAttr='a'></c:child><c:child some_attr='b' anotherAttr='b'/></c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendMoreParentsMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result")
            .expectedBodiesReceived("<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                    + "<c:child some_attr='a' anotherAttr='a'></c:child>"
                                    + "<c:child some_attr='b' anotherAttr='b'/></c:parent></grandparent></g:greatgrandparent>");

        template
            .sendBody("direct:start",
                      "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                      + "<c:child some_attr='a' anotherAttr='a'></c:child><c:child some_attr='b' anotherAttr='b'/></c:parent></grandparent></g:greatgrandparent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendParentMessagesWithDifferentAttributesToTokenize() throws Exception {
        getMockEndpoint("mock:result")
            .expectedBodiesReceived("<?xml version='1.0' encoding='UTF-8'?><g:grandparent xmlns:g='urn:g'><c:parent name='e' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                    + "<c:child some_attr='a' anotherAttr='a'></c:child></c:parent></g:grandparent>",
                                    "<?xml version='1.0' encoding='UTF-8'?><g:grandparent xmlns:g='urn:g'><c:parent name='f' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                                                                                                      + "<c:child some_attr='b' anotherAttr='b'/></c:parent></g:grandparent>");

        template
            .sendBody("direct:start",
                      "<?xml version='1.0' encoding='UTF-8'?><g:grandparent xmlns:g='urn:g'><c:parent name='e' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                      + "<c:child some_attr='a' anotherAttr='a'></c:child></c:parent><c:parent name='f' xmlns:c='urn:c' xmlns:d=\"urn:d\"><c:child some_attr='b' anotherAttr='b'/>"
                                      + "</c:parent></g:grandparent>");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            Namespaces ns = new Namespaces("C", "urn:c");

            public void configure() {
                from("direct:start").split().xtokenize("//C:child", 'w', ns, 2).to("mock:result").end();
            }
        };
    }
}
