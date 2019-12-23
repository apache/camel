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
package org.apache.camel.issues;

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class RedeliveryErrorHandlerAsyncDelayedTwoCamelContextIssueTest {

    @Test
    public void shouldNotBreakRedeliveriesOfSecondContextAfterFirstBeingStopped() throws Exception {
        DefaultCamelContext context1 = createContext();
        ProducerTemplate producer1 = context1.createProducerTemplate();
        ConsumerTemplate consumer1 = context1.createConsumerTemplate();
        context1.start();
        producer1.sendBody("seda://input", "Hey1");
        Exchange ex1 = consumer1.receive("seda://output", 5000);

        DefaultCamelContext context2 = createContext();
        ProducerTemplate producer2 = context2.createProducerTemplate();
        ConsumerTemplate consumer2 = context2.createConsumerTemplate();
        context2.start();

        // now stop 1, and see that 2 is still working
        consumer1.stop();
        producer1.stop();
        context1.stop();

        producer2.sendBody("seda://input", "Hey2");
        Exchange ex2 = consumer2.receive("seda://output", 5000);

        Assert.assertNotNull(ex1);
        Assert.assertEquals("Hey1", ex1.getIn().getBody());
        Assert.assertNotNull(ex2);
        Assert.assertEquals("Hey2", ex2.getIn().getBody());

        consumer2.stop();
        producer2.stop();
        context2.stop();
    }

    private DefaultCamelContext createContext() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).redeliveryDelay(10).maximumRedeliveries(5).maximumRedeliveryDelay(1000).backOffMultiplier(1).asyncDelayedRedelivery();

                from("seda://input").bean(ProblematicBean.class).to("seda://output");
            }
        });
        return context;
    }

    public static final class ProblematicBean {
        int counter;

        public void doSomething() {
            if (counter++ < 2) {
                throw new RuntimeException();
            }
        }
    }

}
