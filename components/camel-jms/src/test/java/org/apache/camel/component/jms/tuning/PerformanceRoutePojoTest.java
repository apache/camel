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
package org.apache.camel.component.jms.tuning;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;

@Ignore
public class PerformanceRoutePojoTest extends CamelSpringTestSupport {

    private int size = 200;
    
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/tuning/PerformanceRoutePojoTest-context.xml");
    }

    @Test
    public void testPojoPerformance() throws Exception {
        long start = System.currentTimeMillis();

        getMockEndpoint("mock:audit").expectedMessageCount(size);
        getMockEndpoint("mock:audit").expectsNoDuplicates().body();

        getMockEndpoint("mock:gold").expectedMessageCount((size / 2) - (size / 10));
        getMockEndpoint("mock:silver").expectedMessageCount(size / 10);

        for (int i = 0; i < size; i++) {
            String type;
            if (i % 10 == 0) {
                type = "silver";
            } else if (i % 2 == 0) {
                type = "gold";
            } else {
                type = "bronze";
            }
            template.sendBodyAndHeader("activemq:queue:inbox", "Message " + i, "type", type);
        }

        assertMockEndpointsSatisfied();

        long delta = System.currentTimeMillis() - start;
        log.info("RoutePerformancePojoTest: Sent: " + size + " Took: " + delta + " ms");
    }

}
