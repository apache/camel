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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;
import org.junit.Test;

public class ManagedValidatorRegistryTest extends ManagementTestSupport {

    @Test
    public void testManageValidatorRegistry() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=services,*"), null);
        List<ObjectName> list = new ArrayList<>(set);
        ObjectName on = null;
        for (ObjectName name : list) {
            if (name.getCanonicalName().contains("DefaultValidatorRegistry")) {
                on = name;
                break;
            }
        }

        assertNotNull("Should have found ValidatorRegistry", on);

        Integer max = (Integer) mbeanServer.getAttribute(on, "MaximumCacheSize");
        assertEquals(1000, max.intValue());

        Integer current = (Integer) mbeanServer.getAttribute(on, "Size");
        assertEquals(3, current.intValue());

        current = (Integer) mbeanServer.getAttribute(on, "StaticSize");
        assertEquals(0, current.intValue());

        current = (Integer) mbeanServer.getAttribute(on, "DynamicSize");
        assertEquals(3, current.intValue());

        String source = (String) mbeanServer.getAttribute(on, "Source");
        assertTrue(source.startsWith("ValidatorRegistry"));
        assertTrue(source.endsWith("capacity: 1000"));

        TabularData data = (TabularData) mbeanServer.invoke(on, "listValidators", null, null);
        assertEquals(3, data.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                validator()
                    .type("xml:foo")
                    .withUri("direct:transformer");
                validator()
                    .type("json:test")
                    .withExpression(body().isNotNull());
                validator()
                    .type("custom")
                    .withJava(MyValidator.class);
                
                from("direct:start").to("mock:result");
            }
        };
    }

    public static class MyValidator extends Validator {
        @Override
        public void validate(Message message, DataType type) throws ValidationException {
            // empty
        }
    }
}
