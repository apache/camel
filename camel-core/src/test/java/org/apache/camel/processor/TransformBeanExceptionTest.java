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

public class TransformBeanExceptionTest extends ContextTestSupport {

    public void testTransformBeanException() throws Exception {
        getMockEndpoint("mock:dead").expectedBodiesReceived("Hello World", "Bye World", "Hi World", "Hi Camel", "Bye Camel");

        template.sendBody("direct:transform", "Hello World");
        template.sendBody("direct:bean", "Bye World");
        template.sendBody("direct:setBody", "Hi World");
        template.sendBody("direct:setHeader", "Hi Camel");
        template.sendBody("direct:setProperty", "Bye Camel");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:transform")
                    .transform().method(TransformBeanExceptionTest.class, "throwUp");

                from("direct:bean")
                    .bean(TransformBeanExceptionTest.class, "throwUp");

                from("direct:setBody")
                    .setBody().method(TransformBeanExceptionTest.class, "throwUp");

                from("direct:setHeader")
                    .setHeader("hello").method(TransformBeanExceptionTest.class, "throwUp");

                from("direct:setProperty")
                    .setProperty("bye").method(TransformBeanExceptionTest.class, "throwUp");
            }
        };
    }

    public static String throwUp(String body) {
        throw new IllegalArgumentException("Forced");
    }
}
