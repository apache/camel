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
package org.apache.camel.component.hazelcast;

import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class HazelcastComponentInstanceReferenceNameSpringTest extends HazelcastCamelSpringTestSupport {

    private static final String TEST_VALUE = "TestValue";
    private static final String TEST_KEY = "TestKey";


    @Test
    public void testComparePutAndGet() {
        template.sendBodyAndHeader("direct:testHazelcastInstanceBeanRefPut", TEST_VALUE,
                HazelcastConstants.OBJECT_ID, TEST_KEY);

        template.sendBodyAndHeader("direct:testHazelcastInstanceBeanRefGet", null,
                HazelcastConstants.OBJECT_ID, TEST_KEY);
        final Object testValueReturn = consumer.receiveBody("seda:out");

        assertEquals(TEST_VALUE, testValueReturn);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/META-INF/spring/test-camel-context-hazelcast-instance-name-reference.xml"
        );
    }
}

