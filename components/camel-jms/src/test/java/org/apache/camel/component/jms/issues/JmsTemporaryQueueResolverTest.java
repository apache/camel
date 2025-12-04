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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.jms.JMSException;
import jakarta.jms.Session;
import jakarta.jms.TemporaryQueue;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.jms.TemporaryQueueResolver;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JmsTemporaryQueueResolverTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @BindToRegistry
    protected MyResolver myResolver = new MyResolver();

    @Test
    public void testTemporaryResolver() {
        String reply =
                template.requestBody("activemq:queue:inJmsTemporaryQueueResolverTest", "Hello World", String.class);
        assertEquals("Bye World", reply);

        assertFalse(myResolver.isDeleted());
        assertTrue(myResolver.isCreated());

        context.stop();
        assertTrue(myResolver.isDeleted());
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:queue:inJmsTemporaryQueueResolverTest")
                        .process(exchange -> exchange.getMessage().setBody("Bye World"));
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }

    private static final class MyResolver implements TemporaryQueueResolver {

        private boolean created;
        private boolean deleted;

        @Override
        public TemporaryQueue createTemporaryQueue(Session session) throws JMSException {
            created = true;
            return session.createTemporaryQueue();
        }

        @Override
        public void delete(TemporaryQueue queue) {
            deleted = true;
        }

        public boolean isCreated() {
            return created;
        }

        public boolean isDeleted() {
            return deleted;
        }
    }
}
