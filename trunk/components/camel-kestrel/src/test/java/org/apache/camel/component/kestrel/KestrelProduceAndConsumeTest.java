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
package org.apache.camel.component.kestrel;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Manual test as you need to start a Kestrel broker")
public class KestrelProduceAndConsumeTest extends CamelTestSupport {

    @EndpointInject(uri = "kestrel://cameltest2")
    private ProducerTemplate producerTemplate;

    @Test
    public void testProduceAndConsume() throws Exception {
        List<String> bodies = new ArrayList<String>();
        for (int k = 0; k < 10; ++k) {
            bodies.add("this is body #" + k);
        }
        
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(bodies.size());
        mock.expectedBodiesReceivedInAnyOrder(bodies);

        for (String body : bodies) {
            producerTemplate.sendBody(body);
        }

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("kestrel://cameltest2?concurrentConsumers=3").to("log:incoming").to("mock:result");
            }
        };
    }
}
