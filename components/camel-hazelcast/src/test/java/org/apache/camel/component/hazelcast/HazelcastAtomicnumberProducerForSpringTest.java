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

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class HazelcastAtomicnumberProducerForSpringTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/META-INF/spring/test-camel-context-atomicnumber.xml");
    }

    @Test
    public void testSet() {
        template.sendBody("direct:set", 4711);

        long body = template.requestBody("direct:get", null, Long.class);
        assertEquals(4711, body);
    }

    @Test
    public void testGet() {
        template.sendBody("direct:set", 1234);

        long body = template.requestBody("direct:get", null, Long.class);
        assertEquals(1234, body);
    }

    @Test
    public void testIncrement() {
        template.sendBody("direct:set", 10);

        long body = template.requestBody("direct:increment", null, Long.class);
        assertEquals(11, body);
    }

    @Test
    public void testDecrement() {
        template.sendBody("direct:set", 10);

        long body = template.requestBody("direct:decrement", null, Long.class);
        assertEquals(9, body);
    }

    @Test
    public void testDestroy() throws InterruptedException {
        template.sendBody("direct:set", 10);
        template.sendBody("direct:destroy", null);
        long body = template.requestBody("direct:get", null, Long.class);
        // the body is destory
        assertEquals(0, body);
    }

}
