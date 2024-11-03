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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.support.builder.PredicateBuilder.not;

public class MockExpectedHeaderXPathTest extends ContextTestSupport {

    protected String filter = "/person[@name='James']";
    protected String name = "/person/@name";
    protected String matchingBody = "<person name='James' city='London'/>";
    protected String notMatchingBody = "<person name='Hiram' city='Tampa'/>";
    protected String notMatchingBody2 = "<person name='Jack' city='Houston'/>";

    @Test
    public void testHeaderXPathBuilderLanguageBuilder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);

        // xpath that takes input from a header
        var xpath = expression().xpath(filter).source("header:cheese").end();

        // validate that some of the headers match and others do not
        mock.message(0).predicate(not(xpath));
        mock.message(1).predicate(xpath);
        mock.message(2).predicate(not(xpath));

        template.sendBodyAndHeader("direct:test", "message 1", "cheese", notMatchingBody);
        template.sendBodyAndHeader("direct:test", "message 2", "cheese", matchingBody);
        template.sendBodyAndHeader("direct:test", "message 3", "cheese", notMatchingBody2);

        mock.assertIsSatisfied();
    }

    @Test
    public void testHeaderXPathPredicate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);

        // validate that some of the headers match and others do not
        mock.message(0).header("cheese").xpath(filter).isFalse();
        mock.message(1).header("cheese").xpath(filter).isTrue();
        mock.message(2).header("cheese").xpath(filter).isFalse();

        template.sendBodyAndHeader("direct:test", "message 1", "cheese", notMatchingBody);
        template.sendBodyAndHeader("direct:test", "message 2", "cheese", matchingBody);
        template.sendBodyAndHeader("direct:test", "message 3", "cheese", notMatchingBody2);

        mock.assertIsSatisfied();
    }

    @Test
    public void testHeaderXPathExpressionAttribute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);

        mock.message(0).header("cheese").xpath(name).isEqualTo("Hiram");
        mock.message(1).header("cheese").xpath(name).isEqualTo("James");
        mock.message(2).header("cheese").xpath(name).isEqualTo("Jack");

        template.sendBodyAndHeader("direct:test", "message 1", "cheese", notMatchingBody);
        template.sendBodyAndHeader("direct:test", "message 2", "cheese", matchingBody);
        template.sendBodyAndHeader("direct:test", "message 3", "cheese", notMatchingBody2);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:test").to("mock:result");
            }
        };
    }
}
