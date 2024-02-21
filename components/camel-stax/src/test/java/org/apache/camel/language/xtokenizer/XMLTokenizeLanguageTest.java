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
package org.apache.camel.language.xtokenizer;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.xmlunit.assertj3.XmlAssert;

public class XMLTokenizeLanguageTest extends CamelTestSupport {

    @Test
    public void testSendClosedTagMessageToTokenize() throws Exception {
        String[] expected = new String[] {
                "<c:child some_attr='a' anotherAttr='a' xmlns:c=\"urn:c\"></c:child>",
                "<c:child some_attr='b' anotherAttr='b' xmlns:c=\"urn:c\"></c:child>" };

        template
                .sendBody("direct:start",
                        "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child some_attr='a' anotherAttr='a'></c:child><c:child some_attr='b' anotherAttr='b'></c:child></c:parent>");

        verify(expected);
    }

    @Test
    public void testSendClosedTagWithLineBreaksMessageToTokenize() throws Exception {
        String[] expected = new String[] {
                "<c:child some_attr='a' anotherAttr='a' xmlns:c=\"urn:c\">\n</c:child>",
                "<c:child some_attr='b' anotherAttr='b' xmlns:c=\"urn:c\">\n</c:child>" };

        template.sendBody("direct:start",
                "<?xml version='1.0' encoding='UTF-8'?>\n" + "<c:parent xmlns:c='urn:c'>\n"
                                          + "<c:child some_attr='a' anotherAttr='a'>\n" + "</c:child>\n"
                                          + "<c:child some_attr='b' anotherAttr='b'>\n" + "</c:child>\n" + "</c:parent>");

        verify(expected);
    }

    @Test
    public void testSendSelfClosingTagMessageToTokenize() throws Exception {
        String[] expected = new String[] {
                "<c:child some_attr='a' anotherAttr='a'  xmlns:c=\"urn:c\"/>",
                "<c:child some_attr='b' anotherAttr='b'  xmlns:c=\"urn:c\"/>" };

        template
                .sendBody("direct:start",
                        "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child some_attr='a' anotherAttr='a' /><c:child some_attr='b' anotherAttr='b' /></c:parent>");

        verify(expected);
    }

    @Test
    public void testSendMixedClosingTagMessageToTokenize() throws Exception {
        String[] expected = new String[] {
                "<c:child some_attr='a' anotherAttr='a' xmlns:c=\"urn:c\">ha</c:child>",
                "<c:child some_attr='b' anotherAttr='b'  xmlns:c=\"urn:c\"/>",
                "<c:child some_attr='c' xmlns:c=\"urn:c\"></c:child>" };

        template.sendBody(
                "direct:start",
                "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child some_attr='a' anotherAttr='a'>ha</c:child>"
                                + "<c:child some_attr='b' anotherAttr='b' /><c:child some_attr='c'></c:child></c:parent>");

        verify(expected);
    }

    @Test
    public void testSendMixedClosingTagInsideMessageToTokenize() throws Exception {
        String[] expected = new String[] {
                "<c:child name='child1' xmlns:c=\"urn:c\"><grandchild name='grandchild1'/> <grandchild name='grandchild2'/></c:child>",
                "<c:child name='child2' xmlns:c=\"urn:c\"><grandchild name='grandchild1'></grandchild><grandchild name='grandchild2'></grandchild></c:child>" };

        template.sendBody(
                "direct:start",
                "<c:parent xmlns:c='urn:c'><c:child name='child1'><grandchild name='grandchild1'/> <grandchild name='grandchild2'/></c:child>"
                                + "<c:child name='child2'><grandchild name='grandchild1'></grandchild><grandchild name='grandchild2'></grandchild></c:child></c:parent>");

        verify(expected);
    }

    @Test
    public void testSendNamespacedChildMessageToTokenize() throws Exception {
        String[] expected = new String[] {
                "<c:child xmlns:c='urn:c' some_attr='a' anotherAttr='a'></c:child>",
                "<c:child xmlns:c='urn:c' some_attr='b' anotherAttr='b' />" };

        template.sendBody("direct:start",
                "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c'><c:child xmlns:c='urn:c' some_attr='a' anotherAttr='a'></c:child>"
                                          + "<c:child xmlns:c='urn:c' some_attr='b' anotherAttr='b' /></c:parent>");

        verify(expected);
    }

    @Test
    public void testSendNamespacedParentMessageToTokenize() throws Exception {
        String[] expected = new String[] {
                "<c:child some_attr='a' anotherAttr='a' xmlns:d=\"urn:d\" xmlns:c=\"urn:c\"></c:child>",
                "<c:child some_attr='b' anotherAttr='b' xmlns:d=\"urn:d\" xmlns:c=\"urn:c\"/>" };

        template.sendBody("direct:start",
                "<?xml version='1.0' encoding='UTF-8'?><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\"><c:child some_attr='a' anotherAttr='a'></c:child>"
                                          + "<c:child some_attr='b' anotherAttr='b'/></c:parent>");

        verify(expected);
    }

    @Test
    public void testSendMoreParentsMessageToTokenize() throws Exception {
        String[] expected = new String[] {
                "<c:child some_attr='a' anotherAttr='a' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"></c:child>",
                "<c:child some_attr='b' anotherAttr='b' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"/>" };

        template
                .sendBody("direct:start",
                        "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt><c:parent xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                          + "<c:child some_attr='a' anotherAttr='a'></c:child><c:child some_attr='b' anotherAttr='b'/></c:parent></grandparent></g:greatgrandparent>");

        verify(expected);
    }

    private void verify(String... expected) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(expected.length);

        MockEndpoint.assertIsSatisfied(context);

        int i = 0;
        for (String target : expected) {
            String body = getMockEndpoint("mock:result").getReceivedExchanges().get(i).getMessage().getBody(String.class);
            XmlAssert.assertThat(body).and(target).areIdentical();
            i++;
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            Namespaces ns = new Namespaces("C", "urn:c");

            public void configure() {
                from("direct:start").split().xtokenize("//C:child", ns).to("mock:result").end();
            }
        };
    }
}
