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
package org.apache.camel.processor.onexception;

import java.io.IOException;
import org.apache.camel.builder.RouteBuilder;

public class OnExceptionComplexWithNestedErrorHandlerRouteTest extends OnExceptionComplexRouteTest {

    public void testNoError3() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start3", "<order><type>myType</type><user>James</user></order>");

        assertMockEndpointsSatisfied();
    }

    public void testFunctionalError3() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:error3").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start3", "<order><type>myType</type><user>Func</user></order>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // global error handler
                // as its based on a unit test we do not have any delays between and do not log the stack trace
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).logStackTrace(false));

                // shared for both routes
                onException(MyTechnicalException.class).handled(true).maximumRedeliveries(2).to("mock:tech.error");

                from("direct:start")
                    // route specific on exception for MyFunctionalException
                    // we MUST use .end() to indicate that this sub block is ended
                    .onException(MyFunctionalException.class).maximumRedeliveries(0).end()
                    .to("bean:myServiceBean")
                    .to("mock:result");

                from("direct:start2")
                    // route specific on exception for MyFunctionalException that is different than the previous route
                    // here we marked it as handled and send it to a different destination mock:handled
                    // we MUST use .end() to indicate that this sub block is ended
                    .onException(MyFunctionalException.class).handled(true).maximumRedeliveries(0).to("mock:handled").end()
                    .to("bean:myServiceBean")
                    .to("mock:result");

                // START SNIPPET: e1
                from("direct:start3")
                    // route specific error handler that is different than the global error handler
                    // here we do not redeliver and send errors to mock:error3 instead of the global endpoint
                    .errorHandler(deadLetterChannel("mock:error3")
                            .maximumRedeliveries(0))

                    // route specific on exception to mark MyFunctionalException as being handled
                    .onException(MyFunctionalException.class).handled(true).end()
                    // however we want the IO exceptions to redeliver at most 3 times
                    .onException(IOException.class).maximumRedeliveries(3).end()
                    .to("bean:myServiceBean")
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}