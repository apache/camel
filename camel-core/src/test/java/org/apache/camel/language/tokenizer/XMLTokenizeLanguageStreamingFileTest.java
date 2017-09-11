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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;

public class XMLTokenizeLanguageStreamingFileTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/xmltokenize");
        super.setUp();
    }

    public void testFromFile() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("<c:child some_attr='a' anotherAttr='a' xmlns:c=\"urn:c\"></c:child>",
                "<c:child some_attr='b' anotherAttr='b' xmlns:c=\"urn:c\"></c:child>",
                "<c:child some_attr='c' anotherAttr='c' xmlns:c=\"urn:c\"></c:child>",
                "<c:child some_attr='d' anotherAttr='d' xmlns:c=\"urn:c\"></c:child>");

        String body = "<?xml version='1.0' encoding='UTF-8'?>"
                + "<c:parent xmlns:c='urn:c'>"
                +   "<c:child some_attr='a' anotherAttr='a'></c:child>"
                +   "<c:child some_attr='b' anotherAttr='b'></c:child>"
                +   "<c:child some_attr='c' anotherAttr='c'></c:child>"
                +   "<c:child some_attr='d' anotherAttr='d'></c:child>"
                + "</c:parent>";

        deleteDirectory("target/xmltokenize");
        template.sendBodyAndHeader("file:target/xmltokenize", body, Exchange.FILE_NAME, "myxml.xml");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            Namespaces ns = new Namespaces("C", "urn:c");
            public void configure() {
                from("file:target/xmltokenize?initialDelay=0&delay=10")
                    .split().xtokenize("//C:child", ns).streaming()
                        .to("mock:result")
                    .end();
            }
        };
    }
}
