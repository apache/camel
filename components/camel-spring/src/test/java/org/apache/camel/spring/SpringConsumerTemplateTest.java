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
package org.apache.camel.spring;

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * Unit test using the ConsumerTemplate
 *
 * @version 
 */
// START SNIPPET: e1
@ContextConfiguration
public class SpringConsumerTemplateTest extends SpringRunWithTestSupport {

    @Autowired
    private ProducerTemplate producer;

    @Autowired
    private ConsumerTemplate consumer;

    @EndpointInject(ref = "result")
    private MockEndpoint mock;

    @Test
    public void testConsumeTemplate() throws Exception {
        // we expect Hello World received in our mock endpoint
        mock.expectedBodiesReceived("Hello World");

        // we use the producer template to send a message to the seda:start endpoint
        producer.sendBody("seda:start", "Hello World");

        // we consume the body from seda:start
        String body = consumer.receiveBody("seda:start", String.class);
        assertEquals("Hello World", body);

        // and then we send the body again to seda:foo so it will be routed to the mock
        // endpoint so our unit test can complete
        producer.sendBody("seda:foo", body);

        // assert mock received the body
        mock.assertIsSatisfied();
    }

}
// END SNIPPET: e1
