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
package org.apache.camel.component.sjms.producer;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.Test;

/**
 * Test Producer prefillPool parameter 
 */
public class PrefillPoolTest extends JmsTestSupport {

    @Test
    public void testProducerWithPrefill() throws Exception {
        sendBodyAndAssert("sjms:queue:producer");
    }

    @Test
    public void testProducerWithoutPrefill() throws Exception {
        sendBodyAndAssert("sjms:queue:producer?prefillPool=false");
    }

    private void sendBodyAndAssert(final String uri) throws InterruptedException {
        String body1 = "Hello World";
        String body2 = "G'day World";
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived(body1, body2);
        template.sendBody(uri, body1);
        template.sendBody(uri, body2);
        result.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("sjms:queue:producer").to("mock:result");
            }
        };
    }
}