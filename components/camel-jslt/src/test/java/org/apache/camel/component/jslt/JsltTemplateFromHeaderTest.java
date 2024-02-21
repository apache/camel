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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class JsltTemplateFromHeaderTest extends CamelTestSupport {

    private static final String TEST_BODY = "{ \"foo\": \"foo\", \"bar\": \"bar\" }";

    @Test
    public void testTemplateInHeaderOnMoreExchanges() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(2);
        getMockEndpoint("mock:result").expectedBodiesReceived(
                "\"foo\"",
                "\"bar\"");

        template.sendBodyAndHeader("direct:start", TEST_BODY,
                JsltConstants.HEADER_JSLT_STRING, ".foo");

        template.sendBodyAndHeader("direct:start", TEST_BODY,
                JsltConstants.HEADER_JSLT_STRING, ".bar");

        MockEndpoint.assertIsSatisfied(context);

    }

    @Test
    public void testTemplateInHeaderOverrideUri() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(2);
        getMockEndpoint("mock:result").expectedBodiesReceived(
                "\"foo\"",
                "\"bar\"");

        template.sendBody("direct:start", TEST_BODY);

        template.sendBodyAndHeader("direct:start", TEST_BODY,
                JsltConstants.HEADER_JSLT_STRING, ".bar");

        MockEndpoint.assertIsSatisfied(context);

    }

    @Test
    public void testTemplateInHeaderOverrideUriOnlyWhenSet() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(2);
        getMockEndpoint("mock:result").expectedBodiesReceived(
                "\"bar\"",
                "\"foo\"");

        template.sendBodyAndHeader("direct:start", TEST_BODY,
                JsltConstants.HEADER_JSLT_STRING, ".bar");

        template.sendBody("direct:start", TEST_BODY);

        MockEndpoint.assertIsSatisfied(context);

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct://start")
                        .to("jslt:org/apache/camel/component/jslt/simple/transformation.jslt?allowTemplateFromHeader=true")
                        .to("mock:result");
            }
        };
    }

}
