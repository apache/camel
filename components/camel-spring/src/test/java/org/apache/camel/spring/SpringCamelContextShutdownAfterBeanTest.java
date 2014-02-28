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

import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringCamelContextShutdownAfterBeanTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/SpringCamelContextShutdownAfterBeanTest.xml");
    }

    public void testShutdown() throws Exception {
        // you may have errors during shutdown, which you can see from the log

        ShutdownOrderBean order = (ShutdownOrderBean) context.getRegistry().lookupByName("order");

        assertEquals(3, order.getStart().size());
        assertEquals(0, order.getShutdown().size());
        assertEquals("a", order.getStart().get(0));
        assertEquals("b", order.getStart().get(1));
        assertEquals("c", order.getStart().get(2));

        MockEndpoint first = getMockEndpoint("mock:first");
        first.expectedMessageCount(5);

        for (int i = 0; i < 5; i++) {
            template.sendBody("seda:start", "Hello World");
        }

        first.assertIsSatisfied();

        // stop spring to cause shutdown of Camel
        applicationContext.close();
        applicationContext.destroy();

        assertEquals(3, order.getStart().size());
        assertEquals(3, order.getShutdown().size());
        assertEquals("c", order.getShutdown().get(0));
        assertEquals("b", order.getShutdown().get(1));
        assertEquals("a", order.getShutdown().get(2));
    }
}
