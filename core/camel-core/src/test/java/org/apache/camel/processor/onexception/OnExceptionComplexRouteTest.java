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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Before;
import org.junit.Test;

public class OnExceptionComplexRouteTest extends ContextTestSupport {

    protected MyServiceBean myServiceBean;

    @Test
    public void testNoError() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "<order><type>myType</type><user>James</user></order>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNoError2() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start2", "<order><type>myType</type><user>James</user></order>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFunctionalError() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        // we use DLC so all exceptions gets handled
        template.sendBody("direct:start", "<order><type>myType</type><user>Func</user></order>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFunctionalError2() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:handled").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start2", "<order><type>myType</type><user>Func</user></order>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTechnicalError() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:tech.error").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "<order><type>myType</type><user>Tech</user></order>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTechnicalError2() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:tech.error").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start2", "<order><type>myType</type><user>Tech</user></order>");

        assertMockEndpointsSatisfied();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        myServiceBean = new MyServiceBean();
        super.setUp();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
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
                // as its based on a unit test we do not have any delays between
                // and do not log the stack trace
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).logStackTrace(false));

                // shared for both routes
                onException(MyTechnicalException.class).handled(true).maximumRedeliveries(2).to("mock:tech.error");

                from("direct:start")
                    // route specific on exception for MyFunctionalException
                    // we MUST use .end() to indicate that this sub block is
                    // ended
                    .onException(MyFunctionalException.class).maximumRedeliveries(0).end().to("bean:myServiceBean").to("mock:result");

                from("direct:start2")
                    // route specific on exception for MyFunctionalException
                    // that is different than the previous route
                    // here we marked it as handled and send it to a different
                    // destination mock:handled
                    // we MUST use .end() to indicate that this sub block is
                    // ended
                    .onException(MyFunctionalException.class).handled(true).maximumRedeliveries(0).to("mock:handled").end().to("bean:myServiceBean").to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
