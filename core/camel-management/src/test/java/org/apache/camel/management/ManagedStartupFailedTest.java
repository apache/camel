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
package org.apache.camel.management;

import java.util.Collections;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ManagedStartupFailedTest extends ManagementTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testAllGood() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").transform(body().prepend("Hello "));
            }
        });

        context.start();

        MBeanServer server = getMBeanServer();
        try {
            Set<ObjectName> onames = server.queryNames(new ObjectName("org.apache.camel:*"), null);
            assertTrue(onames.size() > 0);

            ProducerTemplate producer = context.createProducerTemplate();
            String result = producer.requestBody("direct:start", "Kermit", String.class);
            assertEquals("Hello Kermit", result);
        } finally {
            context.stop();
        }

        Set<ObjectName> onames = server.queryNames(new ObjectName("org.apache.camel:*"), null);
        assertEquals(Collections.emptySet(), onames);
    }

    @Test
    public void testStartupFailure() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("invalid:start");
            }
        });

        try {
            context.start();
            fail("Startup failure expected");
        } catch (Exception ex) {
            // expected
        }

        MBeanServer server = getMBeanServer();
        assertNull(server);
    }
}
