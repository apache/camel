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
package org.apache.camel.processor.resequencer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
public class ResequencerBatchOrderTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ResequencerBatchOrderTest.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").resequence(body()).batch().size(2).timeout(50).to("mock:result");
            }
        };
    }

    @Test
    public void testResequencerBatch() throws Exception {
        for (int i = 0; i < 100; i++) {
            testIteration(i);
        }
    }

    private void testIteration(int i) throws Exception {
        MockEndpoint me = context.getEndpoint("mock:result", MockEndpoint.class);
        me.reset();
        me.expectedMessageCount(4);

        LOG.info("Run #{}", i);

        template.sendBody("direct:start", "4");
        template.sendBody("direct:start", "1");

        template.sendBody("direct:start", "3");
        template.sendBody("direct:start", "2");

        assertMockEndpointsSatisfied();

        // because the order can change a bit depending when the resequencer
        // trigger cut-off
        // then the order can be a bit different

        String a = me.getExchanges().get(0).getIn().getBody(String.class);
        String b = me.getExchanges().get(1).getIn().getBody(String.class);
        String c = me.getExchanges().get(2).getIn().getBody(String.class);
        String d = me.getExchanges().get(3).getIn().getBody(String.class);
        String line = a + b + c + d;

        LOG.info("Order: {}", line);

        assertTrue("1423".equals(line) || "1234".equals(line), "Line was " + line);
    }
}
