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
public class RecipientListStopOnExceptionWithOnExceptionTest extends ContextTestSupport {

    @Test
    public void testRecipientListStopOnException() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:c").expectedMessageCount(0);

        String out = template.requestBodyAndHeader("direct:start", "Hello World", "foo", "direct:a,direct:b,direct:c", String.class);
        assertEquals("Damn Forced", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                    .handled(true)
                    .to("mock:handled")
                    .transform(simple("Damn ${exception.message}"));

                from("direct:start")
                    .recipientList(header("foo")).stopOnException()
                    .to("mock:result");

                from("direct:a").to("mock:a");
                from("direct:b").to("mock:b").throwException(new IllegalArgumentException("Forced"));
                from("direct:c").to("mock:c");
            }
        };
    }
}