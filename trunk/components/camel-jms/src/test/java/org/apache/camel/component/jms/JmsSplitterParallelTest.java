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
package org.apache.camel.component.jms;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsSplitterParallelTest extends CamelTestSupport {

    protected String getUri() {
        return "activemq:queue:foo?useMessageIDAsCorrelationID=true";
    }

    @Test
    public void testSplitParallel() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A,B,C,D,E");
        getMockEndpoint("mock:reply").expectedBodiesReceivedInAnyOrder("Bye A", "Bye B", "Bye C", "Bye D", "Bye E");
        getMockEndpoint("mock:split").expectedBodiesReceivedInAnyOrder("Bye A", "Bye B", "Bye C", "Bye D", "Bye E");

        template.sendBody("direct:start", "A,B,C,D,E");

        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .split(body().tokenize(",")).parallelProcessing()
                        .to("log:before")
                        .to(ExchangePattern.InOut, getUri())
                        .to("log:after")
                        .to("mock:split")
                    .end()
                    .to("mock:result");

                from(getUri())
                    .transform(body().prepend("Bye "))
                    .to("mock:reply");
            }
        };
    }

}
