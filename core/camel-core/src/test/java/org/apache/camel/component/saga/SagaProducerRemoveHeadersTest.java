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
package org.apache.camel.component.saga;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.SagaCompletionMode;
import org.apache.camel.saga.InMemorySagaService;
import org.junit.jupiter.api.Test;

public class SagaProducerRemoveHeadersTest extends ContextTestSupport {

    @Test
    public void testManualCompleteAfterRemoveHeaders() throws InterruptedException {
        MockEndpoint completed = getMockEndpoint("mock:completed");
        completed.expectedMessageCount(1);

        template.sendBody("direct:manual-workflow", "manual-complete");

        completed.assertIsSatisfied();
    }

    @Test
    public void testManualCompensateAfterRemoveHeaders() throws InterruptedException {
        MockEndpoint compensated = getMockEndpoint("mock:compensated");
        compensated.expectedMessageCount(1);

        template.sendBody("direct:manual-workflow", "manual-compensate");

        compensated.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                context.addService(new InMemorySagaService());

                from("direct:manual-workflow")
                        .saga().compensation("mock:compensated").completion("mock:completed")
                        .completionMode(SagaCompletionMode.MANUAL)
                        .removeHeaders("*")
                        .choice()
                        .when(body().isEqualTo(constant("manual-complete")))
                        .to("saga:complete")
                        .when(body().isEqualTo(constant("manual-compensate")))
                        .to("saga:compensate")
                        .end();
            }
        };
    }
}
