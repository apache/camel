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
package org.apache.camel.component.jsonata;

import com.dashjoin.jsonata.Jsonata;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

/**
 * Unit test based on the first sample test from the JSONata project.
 */
class JsonataFrameBindingTest extends CamelTestSupport {

    private JsonataFrameBinding frameBinding = new JsonataFrameBinding() {
        @Override
        public void bindToFrame(Jsonata.Frame clientBuilder) {
            clientBuilder.bind("reverse", (String originalStr) -> {
                String reversedStr = "";

                for (int i = 0; i < originalStr.length(); i++) {
                    reversedStr = originalStr.charAt(i) + reversedStr;
                }

                return reversedStr;
            });
        }
    };

    @Test
    void testFrameBindingJsonata() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(
                IOHelper.loadText(
                        ResourceHelper.resolveMandatoryResourceAsInputStream(
                                context, "org/apache/camel/component/jsonata/frameBinding/output.json"))
                        .trim() // Remove the last newline added by IOHelper.loadText()
        );

        sendBody("direct://start",
                ResourceHelper.resolveMandatoryResourceAsInputStream(
                        context, "org/apache/camel/component/jsonata/frameBinding/input.json"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getComponent("jsonata", JsonataComponent.class).setFrameBinding(frameBinding);
        return new RouteBuilder() {
            public void configure() {
                from("direct://start")
                        .to("jsonata:org/apache/camel/component/jsonata/frameBinding/expressions.spec?inputType=JsonString&outputType=JsonString")
                        .to("mock:result");
            }
        };
    }
}
