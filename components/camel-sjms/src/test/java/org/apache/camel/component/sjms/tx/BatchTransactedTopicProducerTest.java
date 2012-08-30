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
package org.apache.camel.component.sjms.tx;

import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.BatchMessage;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class BatchTransactedTopicProducerTest extends CamelTestSupport {

    @Produce
    protected ProducerTemplate template;

    @Test
    public void testEndpointConfiguredBatchTransaction() throws Exception {
        // We should see the World message twice, once for the exception
        getMockEndpoint("mock:test.prebatch").expectedMessageCount(1);
        getMockEndpoint("mock:test.postbatch").expectedMessageCount(30);

        List<BatchMessage<String>> messages = new ArrayList<BatchMessage<String>>();
        for (int i = 1; i <= 30; i++) {
            String body = "Hello World " + i;
            BatchMessage<String> message = new BatchMessage<String>(body, null);
            messages.add(message);
        }
        template.sendBody("direct:start", messages);

        getMockEndpoint("mock:test.prebatch").assertIsSatisfied();
        getMockEndpoint("mock:test.postbatch").assertIsSatisfied();

    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://broker?broker.persistent=false&broker.useJmx=true");
        SjmsComponent sjms = new SjmsComponent();
        sjms.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", sjms);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                    .to("log:test-before?showAll=true")
                    .to("sjms:topic:batch.topic?transacted=true")
                    .to("mock:test.prebatch");
            
                from("sjms:topic:batch.topic")
                    .to("log:test-after?showAll=true")
                    .to("mock:test.postbatch");
            }
        };
    }
}
