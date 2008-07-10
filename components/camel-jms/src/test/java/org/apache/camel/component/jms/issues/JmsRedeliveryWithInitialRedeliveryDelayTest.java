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
package org.apache.camel.component.jms.issues;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

/**
 * Unit test to verify DLC and JSM based on user reporting
 */
@ContextConfiguration
public class JmsRedeliveryWithInitialRedeliveryDelayTest extends AbstractJUnit38SpringContextTests {

    @Autowired
    protected CamelContext context;

    public void testDLCSpringConfiguredRedeliveryPolicy() throws Exception {
        MockEndpoint dead = context.getEndpoint("mock:dead", MockEndpoint.class);
        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);

        dead.expectedBodiesReceived("Hello World");
        dead.message(0).header("org.apache.camel.Redelivered").isEqualTo(true);
        dead.message(0).header("org.apache.camel.RedeliveryCounter").isEqualTo(4);
        result.expectedMessageCount(0);

        context.createProducerTemplate().sendBody("activemq:in", "Hello World");

        result.assertIsSatisfied();
        dead.assertIsSatisfied();
    }
}
