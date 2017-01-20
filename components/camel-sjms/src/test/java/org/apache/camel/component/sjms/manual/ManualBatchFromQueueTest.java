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
package org.apache.camel.component.sjms.manual;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.batch.ListAggregationStrategy;
import org.apache.camel.component.sjms.batch.SjmsBatchComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Manual test")
public class ManualBatchFromQueueTest extends CamelTestSupport {

    // using failover will automatic re-connect with ActiveMQ
    // private String url = "failover:tcp://localhost:61616";
    private String url = "tcp://localhost:61616";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("testStrategy", new ListAggregationStrategy());

        CamelContext camel = new DefaultCamelContext(registry);

        SjmsBatchComponent sjms = new SjmsBatchComponent();
        sjms.setAsyncStartListener(true);
        log.info("Using live connection to existing ActiveMQ broker running on {}", url);
        sjms.setConnectionFactory(new ActiveMQConnectionFactory(url));

        camel.addComponent("sjms-batch", sjms);

        return camel;
    }

    @Test
    public void testConsume() throws Exception {
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied(1, TimeUnit.MINUTES);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("sjms-batch:queue:foo?asyncStartListener=true&completionSize=3&completionTimeout=60000&aggregationStrategy=#testStrategy")
                    .to("log:foo")
                    .to("mock:foo");
            }
        };
    }
}
