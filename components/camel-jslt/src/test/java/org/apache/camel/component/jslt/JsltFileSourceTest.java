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
package org.apache.camel.component.jslt;

import java.util.Collections;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

/**
 * Unit test based on the JSLT demo playground default values.
 */
public class JsltFileSourceTest extends CamelTestSupport {

    @Test
    public void testJsltAsInputStream() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived(
                IOHelper.loadText(
                        ResourceHelper.resolveMandatoryResourceAsInputStream(
                                context, "org/apache/camel/component/jslt/demoPlayground/output.json"))
                        .trim() // Remove the last newline added by IOHelper.loadText()
        );

        sendBody("direct://start",
                ResourceHelper.resolveMandatoryResourceAsInputStream(
                        context, "org/apache/camel/component/jslt/demoPlayground/input.json"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvalidBody() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        //type integer is not allowed
        sendBody("direct://start", 4);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testJsltAsText() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived(
                IOHelper.loadText(
                        ResourceHelper.resolveMandatoryResourceAsInputStream(
                                context, "org/apache/camel/component/jslt/demoPlayground/output.json"))
                        .trim() // Remove the last newline added by IOHelper.loadText()
        );

        sendBody("direct://start",
                IOHelper.loadText(ResourceHelper.resolveMandatoryResourceAsInputStream(
                        context, "org/apache/camel/component/jslt/demoPlayground/input.json")));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testJsltAsInputStreamPrettyPrint() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived(
                IOHelper.loadText(
                        ResourceHelper.resolveMandatoryResourceAsInputStream(
                                context, "org/apache/camel/component/jslt/demoPlayground/outputPrettyPrint.json"))
                        .trim() // Remove the last newline added by IOHelper.loadText()
        );

        sendBody("direct://startPrettyPrint",
                ResourceHelper.resolveMandatoryResourceAsInputStream(
                        context, "org/apache/camel/component/jslt/demoPlayground/input.json"),
                Collections.singletonMap(JsltConstants.HEADER_JSLT_RESOURCE_URI,
                        "org/apache/camel/component/jslt/demoPlayground/transformation.json"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start")
                        .to("jslt:file:src/test/resources/org/apache/camel/component/jslt/demoPlayground/transformation.json")
                        .to("mock:result");

                from("direct://startPrettyPrint")
                        .to("jslt:dummy?prettyPrint=true&allowTemplateFromHeader=true")
                        .to("mock:result");
            }
        };
    }
}
