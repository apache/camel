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
package org.apache.camel.itest.hawtdb;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class JmsToHawtDBAggregateTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/hawtdb/JmsToHawtDBAggregateTest.xml");
    }

    @Override
    public void setUp() throws Exception {
        // you can disable this to keep the data file on next test
        deleteDirectory("target/data");
        super.setUp();
    }

    @Test
    public void testJmsToHawtDBAggregateTest() throws Exception {
        // number of rounds can be adjusted
        int count = 30;

        getMockEndpoint("mock:input").expectedMessageCount(10 * count);
        getMockEndpoint("mock:out").expectedMessageCount(count);
        getMockEndpoint("mock:result").expectedMessageCount(count);
        getMockEndpoint("mock:result").allMessages().body().isEqualTo("ABCDEFGHIJ");
        getMockEndpoint("mock:result").expectsNoDuplicates(header("counter"));

        for (int i = 0; i < count; i++) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("counter", i);
            map.put("group", "foo");

            template.sendBodyAndHeaders("activemq:queue:foo", "A", map);
            template.sendBodyAndHeaders("activemq:queue:foo", "B", map);
            template.sendBodyAndHeaders("activemq:queue:foo", "C", map);
            template.sendBodyAndHeaders("activemq:queue:foo", "D", map);
            template.sendBodyAndHeaders("activemq:queue:foo", "E", map);
            template.sendBodyAndHeaders("activemq:queue:foo", "F", map);
            template.sendBodyAndHeaders("activemq:queue:foo", "G", map);
            template.sendBodyAndHeaders("activemq:queue:foo", "H", map);
            template.sendBodyAndHeaders("activemq:queue:foo", "I", map);
            template.sendBodyAndHeaders("activemq:queue:foo", "J", map);

            // sleep 1 sec before sending next batch
            Thread.sleep(1000);
        }

        // adjust timeout if you do a very long test
        assertMockEndpointsSatisfied(60, TimeUnit.SECONDS);
    }
}
