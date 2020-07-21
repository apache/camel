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
package org.apache.camel.component.spring.integration;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.Assert.assertTrue;

public class SpringIntegrationMessageTest {

    @Test
    public void testCopyFrom() {
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.start();

        org.springframework.messaging.Message testSpringMessage =
            MessageBuilder.withPayload("Test")
                .setHeader("header1", "value1")
                .setHeader("header2", "value2")
                .build();

        SpringIntegrationMessage original = new SpringIntegrationMessage(camelContext, testSpringMessage);

        SpringIntegrationMessage copy = new SpringIntegrationMessage(camelContext, testSpringMessage);

        copy.copyFrom(original);

        assertTrue(copy.getHeaders().containsKey("header1"));
        assertTrue(copy.getHeaders().containsKey("header2"));
    }

}
