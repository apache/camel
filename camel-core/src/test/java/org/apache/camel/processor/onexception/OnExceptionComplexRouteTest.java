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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;

public class OnExceptionComplexRouteTest extends ContextTestSupport {

    protected MyServiceBean myServiceBean;

    public void testNoError() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "<order><type>myType</type><user>James</user></order>");

        assertMockEndpointsSatisfied();
    }

    public void testNoError2() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start2", "<order><type>myType</type><user>James</user></order>");

        assertMockEndpointsSatisfied();
    }

    public void testFunctionalError() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "<order><type>myType</type><user>Func</user></order>");
            fail("Should have thrown a MyFunctionalException");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(MyFunctionalException.class, e.getCause());
        }

        assertMockEndpointsSatisfied();
    }

    public void testFunctionalError2() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:handled").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start2", "<order><type>myType</type><user>Func</user></order>");

        assertMockEndpointsSatisfied();
    }

    public void testTechnicalError() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:tech.error").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "<order><type>myType</type><user>Tech</user></order>");

        assertMockEndpointsSatisfied();
    }

    public void testTechnicalError2() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:tech.error").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start2", "<order><type>myType</type><user>Tech</user></order>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        myServiceBean = new MyServiceBean();
        super.setUp();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myServiceBean", myServiceBean);
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                // global error handler
                errorHandler(deadLetterChannel("mock:error"));

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
                // END SNIPPET: e1
            }
        };
    }
}
