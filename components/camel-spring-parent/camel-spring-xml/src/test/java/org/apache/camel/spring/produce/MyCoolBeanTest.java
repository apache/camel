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
package org.apache.camel.spring.produce;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContextConfiguration
@ExtendWith(SpringExtension.class)
class MyCoolBeanTest {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void testProducerTemplate() {
        MyCoolBean cool = applicationContext.getBean("cool", MyCoolBean.class);
        assertNotNull(cool, "MyCoolBean should be resolved from application context");
        assertNotNull(cool.producer, "ProducerTemplate should be injected via @Produce annotation");

        // Verify the @Produce annotation correctly wired the template to log:foo
        assertNotNull(cool.producer.getDefaultEndpoint(), "Default endpoint should be configured via @Produce");
        assertEquals("log://foo", cool.producer.getDefaultEndpoint().getEndpointUri(),
                "ProducerTemplate should target log:foo");

        // Verify the CamelContext is started (required for message delivery)
        CamelContext camelContext = applicationContext.getBean(CamelContext.class);
        assertTrue(camelContext.getStatus().isStarted(), "CamelContext should be started");

        // Verify message delivery through the @Produce-injected template
        Exchange result = cool.producer.send(cool.producer.getDefaultEndpoint(),
                e -> e.getMessage().setBody("Hello World"));
        assertFalse(result.isFailed(), "Message delivery to log:foo should succeed");
        assertNull(result.getException(), "No exception should occur during message delivery");
    }
}
