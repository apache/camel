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
import org.junit.Test;

public class TokenizeWrapLanguageTest extends ContextTestSupport {

    @Test
    public void testSendClosedTagMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("<?xml version='1.0' encoding='UTF-8'?><parent><child some_attr='a' anotherAttr='a'></child></parent>", 
                                                              "<?xml version='1.0' encoding='UTF-8'?><parent><child some_attr='b' anotherAttr='b'></child></parent>");

        template.sendBody("direct:start",
            "<?xml version='1.0' encoding='UTF-8'?><parent><child some_attr='a' anotherAttr='a'></child><child some_attr='b' anotherAttr='b'></child></parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendClosedTagWithLineBreaksMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("<?xml version='1.0' encoding='UTF-8'?>\n<parent>\n<child some_attr='a' anotherAttr='a'>\n</child></parent>", 
                                                              "<?xml version='1.0' encoding='UTF-8'?>\n<parent>\n<child some_attr='b' anotherAttr='b'>\n</child></parent>");

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

    @Test
    public void testSendSelfClosingTagMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("<?xml version='1.0' encoding='UTF-8'?><parent><child some_attr='a' anotherAttr='a' /></parent>", 
                                                              "<?xml version='1.0' encoding='UTF-8'?><parent><child some_attr='b' anotherAttr='b' /></parent>");

        template.sendBody("direct:start",
            "<?xml version='1.0' encoding='UTF-8'?><parent><child some_attr='a' anotherAttr='a' /><child some_attr='b' anotherAttr='b' /></parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendMixedClosingTagMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(
            "<?xml version='1.0' encoding='UTF-8'?><parent><child some_attr='a' anotherAttr='a'>ha</child></parent>", 
            "<?xml version='1.0' encoding='UTF-8'?><parent><child some_attr='b' anotherAttr='b' /></parent>", 
            "<?xml version='1.0' encoding='UTF-8'?><parent><child some_attr='c'></child></parent>");

        template.sendBody("direct:start",
            "<?xml version='1.0' encoding='UTF-8'?><parent><child some_attr='a' anotherAttr='a'>ha</child><child some_attr='b' anotherAttr='b' /><child some_attr='c'></child></parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendMixedClosingTagInsideMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(
            "<parent><child name='child1'><grandchild name='grandchild1'/> <grandchild name='grandchild2'/></child></parent>",
            "<parent><child name='child2'><grandchild name='grandchild1'></grandchild><grandchild name='grandchild2'></grandchild></child></parent>");

        template.sendBody("direct:start",
            "<parent><child name='child1'><grandchild name='grandchild1'/> <grandchild name='grandchild2'/></child>"
            + "<child name='child2'><grandchild name='grandchild1'></grandchild><grandchild name='grandchild2'></grandchild></child></parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNamespacedChildMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(
            "<?xml version='1.0' encoding='UTF-8'?><parent><c:child xmlns:c='urn:c' some_attr='a' anotherAttr='a'></c:child></parent>", 
            "<?xml version='1.0' encoding='UTF-8'?><parent><c:child xmlns:c='urn:c' some_attr='b' anotherAttr='b' /></parent>");

        template.sendBody("direct:start",
            "<?xml version='1.0' encoding='UTF-8'?><parent><c:child xmlns:c='urn:c' some_attr='a' anotherAttr='a'></c:child><c:child xmlns:c='urn:c' some_attr='b' anotherAttr='b' /></parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNamespacedParentMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(
            "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\"><c:child some_attr='a' anotherAttr='a'></c:child></c:parent>", 
            "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\"><c:child some_attr='b' anotherAttr='b'/></c:parent>");
        
        template.sendBody("direct:start",
            "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\"><c:child some_attr='a' anotherAttr='a'></c:child><c:child some_attr='b' anotherAttr='b'/></c:parent>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendMoreParentsMessageToTokenize() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(
            "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\">"
            + "<c:child some_attr='a' anotherAttr='a'></c:child></c:parent></grandparent></g:greatgrandparent>", 
            "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\">"
            + "<c:child some_attr='b' anotherAttr='b'/></c:parent></grandparent></g:greatgrandparent>");

        template.sendBody("direct:start",
            "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\">"
            + "<c:child some_attr='a' anotherAttr='a'></c:child><c:child some_attr='b' anotherAttr='b'/></c:parent></grandparent></g:greatgrandparent>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .split().tokenizeXML("child", "*")
                        .to("mock:result")
                    .end();
            }
        };
    }
}
