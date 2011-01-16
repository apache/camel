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
package org.apache.camel.component.jms;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.jencks.amqpool.PooledConnectionFactory;
import org.junit.Test;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
/**
 * @version $Revision$
 */
public class JmsRouteUsingJMSXGroupTest extends CamelTestSupport {

    @Test
    public void testNoConcurrentProducersJMSXGroupID() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducersJMSXGroupID() throws Exception {
        doSendMessages(10, 1);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(files * 2);
        getMockEndpoint("mock:result").expectsNoDuplicates(body());

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        for (int i = 0; i < files; i++) {
            final int index = i;
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    template.sendBodyAndHeader("direct:start", "IBM: " + index, "JMSXGroupID", "IBM");
                    template.sendBodyAndHeader("direct:start", "SUN: " + index, "JMSXGroupID", "SUN");

                    return null;
                }
            });
        }

        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        PooledConnectionFactory pool = new PooledConnectionFactory(new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false&broker.useJmx=false"));
        pool.setMaxConnections(10);

        camelContext.addComponent("jms", jmsComponentAutoAcknowledge(pool));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("jms:queue:foo");

                from("jms:queue:foo?concurrentConsumers=2").to("log:foo?showHeaders=false").to("mock:result");
            }
        };
    }

}