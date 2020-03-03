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

import java.io.IOException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test inspired by user forum.
 */
public class OnExceptionRouteTest extends ContextTestSupport {

    private MyOwnHandlerBean myOwnHandlerBean;
    private MyServiceBean myServiceBean;

    @Test
    public void testNoError() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "<order><type>myType</type><user>James</user></order>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFunctionalError() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        template.sendBody("direct:start", "<order><type>myType</type><user>Func</user></order>");

        assertMockEndpointsSatisfied();
        assertEquals("<order><type>myType</type><user>Func</user></order>", myOwnHandlerBean.getPayload());
    }

    @Test
    public void testTechnicalError() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        template.sendBody("direct:start", "<order><type>myType</type><user>Tech</user></order>");

        assertMockEndpointsSatisfied();
        // should not handle it
        assertNull(myOwnHandlerBean.getPayload());
    }

    @Test
    public void testErrorWhileHandlingException() throws Exception {
        // DLC does not handle the exception as we failed during processing in
        // onException
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMessageCount(0);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "<order><type>myType</type><user>FuncError</user></order>");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            // the myOwnHandlerBean throw exception while handling an exception
            IOException cause = assertIsInstanceOf(IOException.class, e.getCause());
            assertEquals("Damn something did not work", cause.getMessage());
        }

        assertMockEndpointsSatisfied();

        // should not handle it
        assertNull(myOwnHandlerBean.getPayload());
    }

    @Override
    @Before
    public void setUp() throws Exception {
        myOwnHandlerBean = new MyOwnHandlerBean();
        myServiceBean = new MyServiceBean();
        super.setUp();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myOwnHandler", myOwnHandlerBean);
        jndi.bind("myServiceBean", myServiceBean);
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1

                // default should errors go to mock:error
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0));

                // if a MyTechnicalException is thrown we will not try to
                // redeliver and we mark it as handled
                // so the caller does not get a failure
                // since we have no to then the exchange will continue to be
                // routed to the normal error handler
                // destination that is mock:error as defined above
                onException(MyTechnicalException.class).maximumRedeliveries(0).handled(true);

                // if a MyFunctionalException is thrown we do not want Camel to
                // redelivery but handle it our self using
                // our bean myOwnHandler, then the exchange is not routed to the
                // default error (mock:error)
                onException(MyFunctionalException.class).maximumRedeliveries(0).handled(true).to("bean:myOwnHandler");

                // here we route message to our service bean
                from("direct:start").choice().when().xpath("//type = 'myType'").to("bean:myServiceBean").end().to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}
