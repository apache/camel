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
package org.apache.camel.spi;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class SynchronizationTest extends ContextTestSupport {
    
    public void testExchangeAddOnCompletionIssue() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(1);

        MockEndpoint complete = getMockEndpoint("mock:complete");
        complete.expectedBodiesReceivedInAnyOrder("finish", "stop", "ile", "markRollback");

        MockEndpoint failed = getMockEndpoint("mock:failed");
        failed.expectedBodiesReceivedInAnyOrder("faulted", "npe", "rollback");

        template.sendBody("direct:input", "finish");
        template.sendBody("direct:input", "stop");
        template.sendBody("direct:input", "fault");
        template.sendBody("direct:input", "ile");
        template.sendBody("direct:input", "markRollback");

        try {
            template.sendBody("direct:input", "npe");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertEquals("Darn NPE", e.getCause().getMessage());
        }

        try {
            template.sendBody("direct:input", "rollback");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(RollbackExchangeException.class, e.getCause());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:fail-route")
                    .log("failing ${body}")
                    .to("mock:failed");

                from("direct:success-route")
                    .log("completing ${body}")
                    .to("mock:complete");

                from("direct:input")
                    .process(new AddSynchronizationProcessor(template))
                    .onException(IllegalArgumentException.class)
                    .handled(true)
                    .end()
                    .choice()
                    .when(simple("${body} == 'stop'"))
                    .log("stopping")
                    .stop()
                    .when(simple("${body} == 'fault'"))
                    .log("faulting")
                    .setFaultBody(constant("faulted"))
                    .when(simple("${body} == 'ile'"))
                    .log("excepting")
                    .throwException(new IllegalArgumentException("Exception requested"))
                    .when(simple("${body} == 'npe'"))
                    .log("excepting")
                    .throwException(new NullPointerException("Darn NPE"))
                    .when(simple("${body} == 'rollback'"))
                    .log("rollback")
                    .rollback()
                    .when(simple("${body} == 'markRollback'"))
                    .log("markRollback")
                    .markRollbackOnly()
                    .end()
                    .log("finishing")
                    .to("mock:end");
            }
        };
    }

    /**
     * Processor that invokes {@link Exchange#addOnCompletion(Synchronization)} on the exchange. 
     */
    private class AddSynchronizationProcessor implements Processor {

        private ProducerTemplate producerTemplate;

        public AddSynchronizationProcessor(ProducerTemplate template) {
            this.producerTemplate = template;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.addOnCompletion(new Synchronization() {

                @Override
                public void onFailure(Exchange exchange) {
                    producerTemplate.send("direct:fail-route", exchange);
                }

                @Override
                public void onComplete(Exchange exchange) {
                    producerTemplate.send("direct:success-route", exchange);
                }
            });
        }
    }
}
