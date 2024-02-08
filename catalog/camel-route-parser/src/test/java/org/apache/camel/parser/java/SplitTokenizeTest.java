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
package org.apache.camel.parser.java;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class SplitTokenizeTest extends CamelTestSupport {

    @Test
    void testSplitTokenizerA() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceived("Claus", "James", "Willem");

        template.sendBody("direct:a", "Claus,James,Willem");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testSplitTokenizerB() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceived("Claus", "James", "Willem");

        template.sendBodyAndHeader("direct:b", "Hello World", "myHeader", "Claus,James,Willem");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testSplitTokenizerC() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceived("Claus", "James", "Willem");

        template.sendBody("direct:c", "Claus James Willem");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testSplitTokenizerD() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceived("[Claus]", "[James]", "[Willem]");

        template.sendBody("direct:d", "[Claus][James][Willem]");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testSplitTokenizerE() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceived("<person>Claus</person>", "<person>James</person>", "<person>Willem</person>");

        String xml = "<persons><person>Claus</person><person>James</person><person>Willem</person></persons>";
        template.sendBody("direct:e", xml);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testSplitTokenizerEWithSlash() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        String xml = "<persons><person attr='/' /></persons>";
        mock.expectedBodiesReceived("<person attr='/' />");
        template.sendBody("direct:e", xml);
        mock.assertIsSatisfied();
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testSplitTokenizerF() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceived("<person name=\"Claus\"/>", "<person>James</person>", "<person>Willem</person>");

        String xml = "<persons><person/><person name=\"Claus\"/><person>James</person><person>Willem</person></persons>";
        template.sendBody("direct:f", xml);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:a")
                        .split().tokenize(",")
                        .to("mock:split");

                var byHeader = expression().tokenize().token(",").source("header:myHeader").end();
                from("direct:b")
                        .split(byHeader)
                        .to("mock:split");

                from("direct:c")
                        .split().tokenize("(\\W+)\\s*", true).to("mock:split");

                from("direct:d")
                        .split().tokenizePair("[", "]", true)
                        .to("mock:split");

                from("direct:e")
                        .split().tokenizeXML("person")
                        .to("mock:split");

                from("direct:f")
                        .split().xpath("//person")
                        // To test the body is not empty
                        // it will call the ObjectHelper.evaluateValuePredicate()
                        .filter().simple("${body}")
                        .to("mock:split");

            }
        };
    }
}
