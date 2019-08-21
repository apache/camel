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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class MulticastKeepOriginalMessageUnchangedTest extends ContextTestSupport {

    @Test
    public void testUnchanged() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:a").message(0).header("foo").isNull();
        getMockEndpoint("mock:a").message(0).header("bar").isEqualTo("no");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").message(0).header("foo").isNull();
        getMockEndpoint("mock:result").message(0).header("bar").isEqualTo("no");
        getMockEndpoint("mock:foo").expectedBodiesReceived("Foo was here Hello World");
        getMockEndpoint("mock:foo").expectedHeaderReceived("foo", "yes");
        getMockEndpoint("mock:foo").message(0).header("bar").isNull();

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").setHeader("bar", constant("no")).to("mock:a").multicast(AggregationStrategies.useOriginal()).to("direct:foo").end().to("mock:result");

                from("direct:foo").setHeader("foo", constant("yes")).removeHeader("bar").transform().simple("Foo was here ${body}").to("mock:foo");
            }
        };
    }

}
