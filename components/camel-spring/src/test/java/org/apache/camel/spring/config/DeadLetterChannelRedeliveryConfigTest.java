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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringRunWithTestSupport;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * Unit test that spring configured DeadLetterChannel redelivery policy works.
 */
@ContextConfiguration
public class DeadLetterChannelRedeliveryConfigTest extends SpringRunWithTestSupport {

    @Autowired
    protected ProducerTemplate template;

    @Autowired
    protected CamelContext context;

    @Test
    public void testDLCSpringConfiguredRedeliveryPolicy() throws Exception {
        MockEndpoint dead = context.getEndpoint("mock:dead", MockEndpoint.class);
        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);

        dead.expectedBodiesReceived("Hello World");
        // no traces of redelivery headers as DLC handles the exception when moving to DLQ
        dead.message(0).header(Exchange.REDELIVERED).isNull();
        dead.message(0).header(Exchange.REDELIVERY_COUNTER).isNull();
        result.expectedMessageCount(0);

        template.sendBody("direct:in", "Hello World");

        result.assertIsSatisfied();
        dead.assertIsSatisfied();
    }

}
