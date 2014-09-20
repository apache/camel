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
package org.apache.camel.itest.osgi.hazelcast;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.OptionUtils.combine;


@RunWith(PaxExam.class)
public class HazelcastTest extends OSGiIntegrationTestSupport {

    @Test
    public void testAtomicNumber() throws Exception {
        String reply = template.requestBody("direct:getatomic", null, String.class);
        assertNotNull(reply);
        long value = Long.parseLong(reply);
        assertEquals("Should be 0", 0L, value);

        template.sendBody("direct:setatomic", 100L);
        reply = template.requestBody("direct:getatomic", null, String.class);
        assertNotNull(reply);
        value = Long.parseLong(reply);
        assertEquals("Should be 100", 100L, value);
    }

    @Test
    public void testMap() throws Exception {
        SimpleObject item1 = new SimpleObject(1L, "Some value");
        SimpleObject item2 = new SimpleObject(2L, "Some other value");

        template.sendBodyAndHeader("direct:mapput", item1, HazelcastConstants.OBJECT_ID, "1");
        template.sendBodyAndHeader("direct:mapput", item2, HazelcastConstants.OBJECT_ID, "2");

        Object result2 = template.requestBodyAndHeader("direct:mapget", null, HazelcastConstants.OBJECT_ID, "2");
        assertNotNull(result2);
        assertEquals("Should be equal", item2, result2);
        Object result1 = template.requestBodyAndHeader("direct:mapget", null, HazelcastConstants.OBJECT_ID, "1");
        assertNotNull(result1);
        assertEquals("Should be equal", item1, result1);
        Object resul3 = template.requestBodyAndHeader("direct:mapget", null, HazelcastConstants.OBJECT_ID, "3");
        assertNull(resul3);
    }

    @Test
    public void testQueue() throws Exception {
        SimpleObject item1 = new SimpleObject(1L, "Some value");
        SimpleObject item2 = new SimpleObject(2L, "Some other value");

        template.sendBodyAndHeader("direct:queueput", item1, HazelcastConstants.OPERATION, HazelcastConstants.ADD_OPERATION);
        template.sendBodyAndHeader("direct:queueput", item2, HazelcastConstants.OPERATION, HazelcastConstants.ADD_OPERATION);

        Object result1 = template.requestBody("direct:queuepoll", new Object());
        assertNotNull(result1);
        assertEquals("Should be equal", item1, result1);
        Object result2 = template.requestBody("direct:queuepoll", new Object());
        assertNotNull(result2);
        assertEquals("Should be equal", item1, result1);
        Object resul3 = template.requestBody("direct:queuepoll", new Object());
        assertNull(resul3);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                //Atomic number
                from("direct:getatomic")
                        .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.GET_OPERATION))
                        .toF("hazelcast:atomicvalue:myvalue");

                from("direct:setatomic")
                        .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.SETVALUE_OPERATION))
                        .toF("hazelcast:atomicvalue:myvalue");

                //Map
                from("direct:mapput")
                        .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.PUT_OPERATION))
                        .toF("hazelcast:map:mymap", HazelcastConstants.MAP_PREFIX);

                from("direct:mapget")
                        .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.GET_OPERATION))
                        .toF("hazelcast:map:mymap");

                //Queue
                from("direct:queueput")
                        .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.PUT_OPERATION))
                        .toF("hazelcast:queue:myqueue");

                from("direct:queuepoll")
                        .setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.POLL_OPERATION))
                        .toF("hazelcast:queue:myqueue");
            }
        };
    }

    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components
            loadCamelFeatures("camel-hazelcast"));

        return options;
    }
}
