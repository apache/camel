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
package org.apache.camel.component.saga;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.saga.InMemorySagaService;
import org.apache.camel.model.SagaCompletionMode;
import org.junit.Assert;

/**
 *
 */
public class SagaComponentTest extends ContextTestSupport {

    public void testManualCompletion() throws InterruptedException {
        MockEndpoint completed = getMockEndpoint("mock:completed");
        completed.expectedMessageCount(1);

        template.sendBody("direct:manual-workflow", "manual-complete");

        completed.assertIsSatisfied();
    }

    public void testManualCompletionIsNotTriggeredAutomatically() throws InterruptedException {
        MockEndpoint completed = getMockEndpoint("mock:completed");
        completed.expectedMessageCount(1);
        completed.setResultWaitTime(1000);

        template.sendBody("direct:manual-workflow", "do-not-complete");

        completed.assertIsNotSatisfied();
    }

    public void testManualCompensationIsTriggeredOnly() throws InterruptedException {
        MockEndpoint completed = getMockEndpoint("mock:completed");
        completed.expectedMessageCount(1);
        completed.setResultWaitTime(1000);

        MockEndpoint compensated = getMockEndpoint("mock:compensated");
        compensated.expectedMessageCount(1);

        template.sendBody("direct:manual-workflow", "manual-compensate");

        completed.assertIsNotSatisfied();
        compensated.assertIsSatisfied();
    }

    public void testAutoCompletion() throws InterruptedException {
        MockEndpoint completed = getMockEndpoint("mock:completed");
        completed.expectedMessageCount(1);

        template.sendBody("direct:auto-workflow", "auto-complete");

        completed.assertIsSatisfied();
    }

    public void testAutoCompensationIsTriggeredOnly() throws InterruptedException {
        MockEndpoint completed = getMockEndpoint("mock:completed");
        completed.expectedMessageCount(1);
        completed.setResultWaitTime(1000);

        MockEndpoint compensated = getMockEndpoint("mock:compensated");
        compensated.expectedMessageCount(1);

        try {
            template.sendBody("direct:auto-workflow", "auto-compensate");
            Assert.fail("Should throw an exception");
        } catch (Exception ex) {
            // OK
        }

        completed.assertIsNotSatisfied();
        compensated.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                context.addService(new InMemorySagaService());

                // Manual complete/compensate
                from("direct:manual-workflow")
                        .saga()
                        .compensation("mock:compensated")
                        .completion("mock:completed")
                        .completionMode(SagaCompletionMode.MANUAL)
                        .to("seda:async");

                from("seda:async")
                        .choice()
                        .when(body().isEqualTo(constant("manual-complete")))
                        .to("saga:complete")
                        .when(body().isEqualTo(constant("manual-compensate")))
                        .to("saga:compensate")
                        .end();


                // Auto complete/compensate
                from("direct:auto-workflow")
                        .saga()
                        .completion("mock:completed")
                        .compensation("mock:compensated")
                        .choice()
                        .when(body().isEqualTo(constant("auto-compensate")))
                        .process(x -> {
                            throw new RuntimeException("mock exception");
                        })
                        .end()
                        .to("seda:async");
            }
        };
    }
}
