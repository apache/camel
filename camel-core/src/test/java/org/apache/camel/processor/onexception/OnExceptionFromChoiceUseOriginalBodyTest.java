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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * Unit test for CAMEL-1188
 */
public class OnExceptionFromChoiceUseOriginalBodyTest extends ContextTestSupport {

    private MyServiceBean myServiceBean;

    public void testNoErrorWhen() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:func").expectedMessageCount(0);
        getMockEndpoint("mock:tech").expectedMessageCount(0);
        getMockEndpoint("mock:otherwise").expectedMessageCount(0);

        MockEndpoint mock = getMockEndpoint("mock:when");
        mock.expectedMessageCount(1);

        template.sendBody("direct:route", "<order><type>myType</type><user>James</user></order>");

        assertMockEndpointsSatisfied();
    }

    public void testFunctionalError() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:tech").expectedMessageCount(0);
        getMockEndpoint("mock:when").expectedMessageCount(0);
        getMockEndpoint("mock:otherwise").expectedMessageCount(0);

        MockEndpoint mock = getMockEndpoint("mock:func");
        mock.expectedBodiesReceived("Func");

        template.sendBody("direct:func", "Func");

        assertMockEndpointsSatisfied();
    }

    public void testTechnicalError() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:func").expectedMessageCount(0);
        getMockEndpoint("mock:when").expectedMessageCount(0);
        getMockEndpoint("mock:otherwise").expectedMessageCount(0);

        MockEndpoint mock = getMockEndpoint("mock:tech");
        mock.expectedBodiesReceived("Tech");

        template.sendBody("direct:tech", "Tech");

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
                errorHandler(deadLetterChannel("mock:error"));

                onException(MyTechnicalException.class).useOriginalMessage().maximumRedeliveries(0).handled(true).to("mock:tech");
                onException(MyFunctionalException.class).useOriginalMessage().maximumRedeliveries(0).handled(true).to("mock:func");

                from("direct:tech")
                    .setBody(constant("<order><type>myType</type><user>Tech</user></order>"))
                    .to("direct:route");

                from("direct:func")
                    .setBody(constant("<order><type>myType</type><user>Func</user></order>"))
                    .to("direct:route");


                from("direct:route")
                    .choice()
                        .when(method("myServiceBean").isEqualTo("James")).to("mock:when")
                    .otherwise()
                        .to("mock:otherwise");
            }
        };
    }


}