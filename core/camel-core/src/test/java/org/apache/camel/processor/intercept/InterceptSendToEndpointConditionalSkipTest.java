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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Unit tests on the conditional skip support on InterceptSendToEndpoint.
 */
public class InterceptSendToEndpointConditionalSkipTest extends ContextTestSupport {

    /**
     * Verify that the endpoint is only skipped if the adjacent 'when' condition
     * is satisfied
     */
    @Test
    public void testInterceptSendToEndpointSkipConditionSatisfied() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:skippable").expectedMessageCount(0);
        getMockEndpoint("mock:detour").expectedMessageCount(1);
        getMockEndpoint("mock:c").expectedMessageCount(1);

        template.sendBody("direct:start", "skip");

        assertMockEndpointsSatisfied();
    }

    /**
     * Verify that the endpoint is not skipped if the adjacent 'when' condition
     * evaluates to false
     */
    @Test
    public void testInterceptSendToEndpointSkipConditionNotSatisfied() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:skippable").expectedMessageCount(1);
        getMockEndpoint("mock:detour").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    /**
     * Verify that the conditional skip support is only activated when using
     * interceptSendToEndpoint().when() and not
     * interceptSendToEndpoint().choice()..., as the choice keyword is not
     * directly associated with the interception behaviour and it belongs to the
     * interception body (initiating a new routing block)
     */
    @Test
    public void testInterceptSendToEndpointSkipConditionNoEffectChoice() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(2);
        getMockEndpoint("mock:skippableNoEffect").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(2);

        getMockEndpoint("mock:noSkipWhen").expectedMessageCount(1);
        getMockEndpoint("mock:noSkipOW").expectedMessageCount(1);

        template.sendBody("direct:startNoEffect", "skipNoEffectWhen");
        template.sendBody("direct:startNoEffect", "Hello Camel");

        assertMockEndpointsSatisfied();
    }

    /**
     * Test that when multiple conditions are chained together in Java DSL, only
     * the first one will determine whether the endpoint is skipped or not
     */
    @Test
    public void testInterceptSendToEndpointSkipMultipleConditions() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:skippableMultipleConditions").expectedMessageCount(0);
        getMockEndpoint("mock:detour").expectedMessageCount(1);
        getMockEndpoint("mock:c").expectedMessageCount(1);

        template.sendBody("direct:startMultipleConditions", "skip");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // only skip if the body equals 'skip'
                interceptSendToEndpoint("mock:skippable").skipSendToOriginalEndpoint().when(body().isEqualTo("skip")).to("mock:detour");

                // always skip with a normal with a normal choice inside
                // instructing where to route instead
                interceptSendToEndpoint("mock:skippableNoEffect").skipSendToOriginalEndpoint().choice().when(body().isEqualTo("skipNoEffectWhen")).to("mock:noSkipWhen").otherwise()
                    .to("mock:noSkipOW");

                // in this case, the original endpoint will be skipped but no
                // message will be sent to mock:detour
                interceptSendToEndpoint("mock:skippableMultipleConditions").skipSendToOriginalEndpoint().when(body().isEqualTo("skip")).when(body().isNotEqualTo("skip"))
                    .to("mock:detour");

                from("direct:start").to("mock:a").to("mock:skippable").to("mock:c");

                from("direct:startNoEffect").to("mock:a").to("mock:skippableNoEffect").to("mock:c");

                from("direct:startMultipleConditions").to("mock:a").to("mock:skippableMultipleConditions").to("mock:c");
            }
        };
    }

}
