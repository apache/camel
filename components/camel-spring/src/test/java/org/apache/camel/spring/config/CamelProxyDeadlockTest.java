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
package org.apache.camel.spring.config;

import org.apache.camel.Exchange;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CamelProxyDeadlockTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/config/CamelProxyDeadlockTest.xml");
    }

    @Test
    public void testCamelProxy() throws Exception {
        SpringCamelContext scc = context.adapt(SpringCamelContext.class);
        DefaultProducerTemplate producer = new DefaultProducerTemplate(scc);
        producer.start();

        Object reply = producer.requestBody("direct-vm:start", "Hello!");

        assertEquals("Hello from Piotr Klimczak", reply);
    }

    interface TestProcessor {
        Object test(Exchange exchange);
    }
}
