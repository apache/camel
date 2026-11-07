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
package org.apache.camel.component.activemq;

import jakarta.jms.JMSException;

import org.apache.camel.CamelContext;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The pool that the component creates for an endpoint from the broker URL stays usable when the component is stopped
 * and started again, and is stopped when Camel stops. No broker listens on the broker URL: a usable pool tries to
 * connect and fails, a stopped pool gives no connection.
 */
public class ActiveMQComponentRestartTest {

    private static JmsPoolConnectionFactory pool(CamelContext context) {
        JmsEndpoint endpoint = context.getEndpoint("activemq:queue:restart", JmsEndpoint.class);
        return assertInstanceOf(JmsPoolConnectionFactory.class, endpoint.getConfiguration().getOrCreateConnectionFactory());
    }

    private static CamelContext context() {
        ActiveMQComponent activemq = new ActiveMQComponent();
        activemq.setBrokerURL("tcp://localhost:1");
        CamelContext context = new DefaultCamelContext();
        context.addComponent("activemq", activemq);
        return context;
    }

    @Test
    public void poolIsUsableAfterTheComponentIsStoppedAndStarted() throws Exception {
        try (CamelContext context = context()) {
            context.start();
            JmsPoolConnectionFactory pool = pool(context);

            ActiveMQComponent activemq = context.getComponent("activemq", ActiveMQComponent.class);
            activemq.stop();
            activemq.start();

            assertThrows(JMSException.class, pool::createConnection);
        }
    }

    @Test
    public void poolIsStoppedWhenTheContextStops() throws Exception {
        JmsPoolConnectionFactory pool;
        try (CamelContext context = context()) {
            context.start();
            pool = pool(context);
        }

        assertNull(pool.createConnection());
    }
}
