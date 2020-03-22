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

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Unit test for issues CAMEL-1034 and CAMEL-1037
 */
public class JmsResequencerTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/issues/JmsResequencerTest-context.xml");
    }

    @Test
    public void testBatchResequencer() throws Exception {
        testResequencer("activemq:queue:in1");
    }

    @Test
    public void testStreamResequencer() throws Exception {
        testResequencer("activemq:queue:in2");
    }
    
    private void testResequencer(String endpoint) throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(100);

        for (int i = 0; i < 100; i++) {
            result.message(i).body().isEqualTo(i + 1);
        }

        for (int i = 100; i > 0; i--) {
            // send as text messages (not java objects - as they are not serializable and allowed by JMS brokers like ActiveMQ)
            String text = "" + i;
            template.sendBodyAndHeader(endpoint, text, "num", (long) i);
        }

        assertMockEndpointsSatisfied();
    }

}
