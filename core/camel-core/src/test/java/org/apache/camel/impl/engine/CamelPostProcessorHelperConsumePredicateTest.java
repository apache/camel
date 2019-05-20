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
package org.apache.camel.impl.engine;

import java.lang.reflect.Method;

import org.apache.camel.Consume;
import org.apache.camel.ContextTestSupport;
import org.junit.Test;

public class CamelPostProcessorHelperConsumePredicateTest extends ContextTestSupport {

    @Test
    public void testConsumePredicate() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyConsumeBean my = new MyConsumeBean();

        Method method = my.getClass().getMethod("low", String.class);
        helper.consumerInjection(method, my, "foo");
        method = my.getClass().getMethod("high", String.class);
        helper.consumerInjection(method, my, "foo");

        getMockEndpoint("mock:low").expectedBodiesReceived("17", "89", "39");
        getMockEndpoint("mock:high").expectedBodiesReceived("219", "112");

        template.sendBody("direct:foo", "17");
        template.sendBody("direct:foo", "219");
        template.sendBody("direct:foo", "89");
        template.sendBody("direct:foo", "112");
        template.sendBody("direct:foo", "39");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConsumePredicateDrop() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyConsumeBean my = new MyConsumeBean();

        Method method = my.getClass().getMethod("low", String.class);
        helper.consumerInjection(method, my, "foo");
        method = my.getClass().getMethod("high", String.class);
        helper.consumerInjection(method, my, "foo");

        getMockEndpoint("mock:low").expectedBodiesReceived("17");
        getMockEndpoint("mock:high").expectedBodiesReceived("112");

        template.sendBody("direct:foo", "17");
        // should be dropped as it does not match any predicates
        template.sendBody("direct:foo", "-1");
        template.sendBody("direct:foo", "112");

        assertMockEndpointsSatisfied();
    }

    public class MyConsumeBean {

        @Consume(value = "direct:foo", predicate = "${body} >= 0 && ${body} < 100")
        public void low(String body) {
            template.sendBody("mock:low", body);
        }

        @Consume(value = "direct:foo", predicate = "${body} >= 100")
        public void high(String body) {
            template.sendBody("mock:high", body);
        }
    }

}
