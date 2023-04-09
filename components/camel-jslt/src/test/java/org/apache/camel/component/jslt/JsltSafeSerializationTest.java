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

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;

public class JsltSafeSerializationTest extends CamelTestSupport {

    @Test
    public void testSafeHeaderSerialization() throws Exception {
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived(
                IOHelper.loadText(
                        ResourceHelper.resolveMandatoryResourceAsInputStream(
                                context, "org/apache/camel/component/jslt/serialization/output.json"))
                        .trim() // Remove the last newline added by IOHelper.loadText()
        );

        final Exchange resultExchange = template().send("direct://start",
                exchange -> {
                    exchange.getIn().setBody(IOHelper.loadText(ResourceHelper.resolveMandatoryResourceAsInputStream(
                            context, "org/apache/camel/component/jslt/serialization/input.json")));
                    exchange.getIn().setHeader("unsafe", new UnsafeBean());
                    exchange.getIn().setHeader("safe", new SafeBean());
                    exchange.getIn().setHeader("array", List.of(1, 2, 3));
                    exchange.getIn().setHeader("map", Map.of("a", new UnsafeBean()));
                });

        MockEndpoint.assertIsSatisfied(context);

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start")
                        .to("jslt:org/apache/camel/component/jslt/serialization/transformation.jslt")
                        .to("mock:result");
            }
        };
    }

}
