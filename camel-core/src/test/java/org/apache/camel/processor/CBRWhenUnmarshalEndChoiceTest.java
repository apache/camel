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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class CBRWhenUnmarshalEndChoiceTest extends ContextTestSupport {

    @Test
    public void testCBR() throws Exception {
        getMockEndpoint("mock:when").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:abc", "Hello World");
        template.sendBody("direct:abc", null);
        template.sendBody("direct:abc", "Please do not fail");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:abc")
                    .filter(body().isNotNull())
                        .choice()
                            .when().simple("${body} == 'Please do not fail'")
                                .to("mock:when")
                                .unmarshal().string().endChoice()
                            .otherwise()
                                .to("mock:other")
                            .end()
                        .to("mock:result")
                    .end();
            }
        };
    }
}
