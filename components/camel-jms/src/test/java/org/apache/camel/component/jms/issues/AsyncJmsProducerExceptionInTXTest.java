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
package org.apache.camel.component.jms.issues;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-4616">CAMEL-4616</a>
 * @see <a href="https://activemq.apache.org/producer-flow-control.html">ActiveMQ flow control</a>
 */
@Disabled("Cannot reproduce with Artemis")
class AsyncJmsProducerExceptionInTXTest extends CamelBrokerClientTestSupport {

    @BeforeAll
    static void setSystemProperties() {
        // configure classpath:org/apache/camel/component/jms/issues/broker.xml
        System.setProperty("producer-flow-control", "true");
        System.setProperty("send-fail-if-no-space", "true");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:transacted_start")
                        .transacted()
                        // send first 251 kb - need to exceed 500 kb in at least two steps as ActiveMQ doesn't fail in one step
                        .setBody(constant("X".repeat(251 * 1024)))
                        .to("direct:jmsProducerEndpoint")
                        // send second 251 kb to exceed 500 kb and so to trigger ResourceAllocationException
                        .setBody(constant("Y".repeat(251 * 1024)))
                        .to("direct:jmsProducerEndpoint");

                from("direct:jmsProducerEndpoint")
                        // deliveryMode=1 (NON_PERSISTENT) to use Async Sends - generally speaking, producers of non-persistent messages
                        .to("activemq:queue:AsyncJmsProducerExceptionInTXTest?deliveryMode=1");

                from("direct:non_transacted_start")
                        .setBody(constant("X".repeat(251 * 1024)))
                        .to("direct:jmsProducerEndpoint")
                        .setBody(constant("Y".repeat(251 * 1024)))
                        .to("direct:jmsProducerEndpoint");
            }
        };
    }

    @Test
    void testAsyncEndpointException() {
        try {
            template.sendBody("direct:transacted_start", null);
            fail("transaction should fail, otherwise looks like CAMEL-4616 has been emerged!");
        } catch (CamelExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof TransactionException);
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertEquals(
                    "Usage Manager Memory Limit reached on queue://AsyncJmsProducerExceptionInTXTest. See http://activemq.apache.org/producer-flow-control.html for more info",
                    cause.getMessage());
        }

        // non-transacted not fail assertion
        template.sendBody("direct:non_transacted_start", null);
    }
}
