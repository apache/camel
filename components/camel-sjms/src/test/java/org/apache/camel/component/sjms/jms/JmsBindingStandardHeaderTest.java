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
package org.apache.camel.component.sjms.jms;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import jakarta.jms.Message;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsBindingStandardHeaderTest {

    @Test
    public void testStandardJmsHeaderInAnyCase() throws Exception {
        // records the calls of the setters on the JMS message
        Map<String, Object> calls = new HashMap<>();
        Message message = (Message) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Message.class },
                (proxy, method, args) -> {
                    if (method.getName().startsWith("set")) {
                        calls.put(method.getName(), args[args.length - 1]);
                    }
                    return null;
                });

        JmsBinding binding = new JmsBinding(true, false, null, null, null, null);
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            binding.appendJmsProperty(message, exchange, "jmscorrelationid", "123");
            binding.appendJmsProperty(message, exchange, "JMSTYPE", "myType");
            binding.appendJmsProperty(message, exchange, "jmsPriority", 7);
        }

        assertEquals(Map.of("setJMSCorrelationID", "123", "setJMSType", "myType", "setJMSPriority", 7), calls);
    }
}
