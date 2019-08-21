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
package org.apache.camel.processor.onexception;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class OnExceptionComplexWithNestedErrorHandlerRouteWithDefaultErrorHandlerTest extends OnExceptionComplexWithNestedErrorHandlerRouteTest {

    @Override
    @Test
    public void testFunctionalError() throws Exception {
        // override as we dont support redelivery with DefaultErrorHandler
        // then mock error should not receive any messages
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "<order><type>myType</type><user>Func</user></order>");
            fail("Should have thrown a MyFunctionalException");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(MyFunctionalException.class, e.getCause());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // shared for both routes
                onException(MyTechnicalException.class).handled(true).to("mock:tech.error");

                from("direct:start")
                    // route specific on exception for MyFunctionalException
                    // we MUST use .end() to indicate that this sub block is
                    // ended
                    .onException(MyFunctionalException.class).handled(false).end().to("bean:myServiceBean").to("mock:result");

                from("direct:start2")
                    // route specific on exception for MyFunctionalException
                    // that is different than the previous route
                    // here we marked it as handled and send it to a different
                    // destination mock:handled
                    // we MUST use .end() to indicate that this sub block is
                    // ended
                    .onException(MyFunctionalException.class).handled(true).to("mock:handled").end().to("bean:myServiceBean").to("mock:result");

                // START SNIPPET: e1
                from("direct:start3")
                    // route specific error handler that is different than the
                    // global error handler
                    // here we do not redeliver and send errors to mock:error3
                    // instead of the global endpoint
                    .errorHandler(deadLetterChannel("mock:error3").maximumRedeliveries(0))

                    // route specific on exception to mark MyFunctionalException
                    // as being handled
                    .onException(MyFunctionalException.class).handled(true).end().to("bean:myServiceBean").to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
