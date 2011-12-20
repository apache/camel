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

import org.apache.camel.test.junit4.CamelSpringTestSupport;
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

    /*
     * will be fixed in next hazelcast version (1.9.3). Mail from Talip (21.02.2011):
     * 
     * I see. Hazelcast.shutdownAll() should cleanup instances (maps/queues). I just fixed it.
     * 
     * AtomicNumber.destroy() should also destroy the number and if you call atomicNumber.get() after the destroy it should throw IllegalStateException. It is also fixed.
     * 
     * set test to true by default. TODO: if we'll get the new hazelcast version I'll fix the test.
     */
    @Test
    public void testDestroy() {
        template.sendBody("direct:set", 10);
        template.sendBody("direct:destroy", null);

        // assertTrue(Hazelcast.getInstances().isEmpty());
    }

}
