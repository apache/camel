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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class MulticastDslTest extends ContextTestSupport {
    @Test
    public void testMulticastDsl() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("onPrepare", true);
        mock.expectedBodiesReceived(5);

        template.sendBody("direct:start", 1);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").multicast().onPrepare().message(m -> m.setHeader("onPrepare", true)).aggregationStrategy().body(Integer.class, (o, n) -> o != null ? o + n : n)
                    .to("direct:increase-by-1").to("direct:increase-by-2").end().to("mock:result");

                from("direct:increase-by-1").bean(new Increase(1));
                from("direct:increase-by-2").bean(new Increase(2));
            }
        };
    }

    public static class Increase {
        private final int amount;

        public Increase(int amount) {
            this.amount = amount;
        }

        public int add(int num) {
            return num + amount;
        }
    }
}
