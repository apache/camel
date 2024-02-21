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
package org.apache.camel.component.jms.tx;

import org.apache.camel.component.jms.AbstractSpringJMSTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

@Tags({ @Tag("not-parallel"), @Tag("spring"), @Tag("tx") })
public class JMSTransactionThrottlingRoutePolicyTest extends AbstractSpringJMSTestSupport {

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/org/apache/camel/component/jms/tx/JMSTransactionThrottlingRoutePolicyTest.xml");
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("activemq-data");
        super.setUp();
    }

    @Test
    public void testJmsTransactedThrottlingRoutePolicy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        int size = 200;
        mock.expectedMinimumMessageCount(size);

        for (int i = 0; i < size; i++) {
            template.sendBody("activemq-sender:queue:JMSTransactionThrottlingRoutePolicyTest", "Message " + i);
        }

        MockEndpoint.assertIsSatisfied(context);
    }

}
