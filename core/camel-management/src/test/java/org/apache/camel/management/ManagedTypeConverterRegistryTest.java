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

import java.util.Set;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_SERVICE;
import static org.junit.jupiter.api.Assertions.*;

@DisabledOnOs(OS.AIX)
public class ManagedTypeConverterRegistryTest extends ManagementTestSupport {

    @Test
    public void testTypeConverterRegistry() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(2);

        template.sendBody("direct:start", "3");
        template.sendBody("direct:start", "7");

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getCamelObjectName(TYPE_SERVICE, "*");

        // number of services
        Set<ObjectName> names = mbeanServer.queryNames(on, null);
        ObjectName name = null;
        for (ObjectName service : names) {
            if (service.toString().contains("DefaultTypeConverter")) {
                name = service;
                break;
            }
        }
        assertNotNull(name, "Cannot find DefaultTypeConverter");

        // is disabled by default
        Boolean enabled = (Boolean) mbeanServer.getAttribute(name, "StatisticsEnabled");
        assertEquals(Boolean.FALSE, enabled);

        // need to enable statistics
        mbeanServer.setAttribute(name, new Attribute("StatisticsEnabled", Boolean.TRUE));

        Long failed = (Long) mbeanServer.getAttribute(name, "FailedCounter");
        assertEquals(0, failed.intValue());
        Long miss = (Long) mbeanServer.getAttribute(name, "MissCounter");
        assertEquals(0, miss.intValue());

        // reset
        mbeanServer.invoke(name, "resetTypeConversionCounters", null, null);

        template.sendBody("direct:start", "5");

        // should hit
        Long hit = (Long) mbeanServer.getAttribute(name, "HitCounter");
        assertEquals(1, hit.intValue());
        failed = (Long) mbeanServer.getAttribute(name, "FailedCounter");
        assertEquals(0, failed.intValue());
        miss = (Long) mbeanServer.getAttribute(name, "MissCounter");
        assertEquals(2, miss.intValue());  // stream caching misses

        // reset
        mbeanServer.invoke(name, "resetTypeConversionCounters", null, null);

        try {
            template.sendBody("direct:start", "foo");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // expected
        }

        // should now have a failed
        failed = (Long) mbeanServer.getAttribute(name, "FailedCounter");
        assertEquals(1, failed.intValue());
        miss = (Long) mbeanServer.getAttribute(name, "MissCounter");
        assertEquals(1, miss.intValue());  // stream caching misses

        // reset
        mbeanServer.invoke(name, "resetTypeConversionCounters", null, null);

        failed = (Long) mbeanServer.getAttribute(name, "FailedCounter");
        assertEquals(0, failed.intValue());
        miss = (Long) mbeanServer.getAttribute(name, "MissCounter");
        assertEquals(0, miss.intValue());

        // we have more than 150 converters out of the box
        Integer converters = (Integer) mbeanServer.getAttribute(name, "NumberOfTypeConverters");
        assertTrue(converters >= 150, "Should be more than 150 converters, was: " + converters);

        Boolean has = (Boolean) mbeanServer.invoke(name, "hasTypeConverter",
                new Object[] { "java.lang.String", "java.io.InputStream" },
                new String[] { "java.lang.String", "java.lang.String" });
        assertTrue(has, "Should have type converter");

        has = (Boolean) mbeanServer.invoke(name, "hasTypeConverter", new Object[] { "java.math.BigInteger", "int" },
                new String[] { "java.lang.String", "java.lang.String" });
        assertTrue(has, "Should have type converter");

        has = (Boolean) mbeanServer.invoke(name, "hasTypeConverter",
                new Object[] { "java.math.BigInteger", "java.util.Random" },
                new String[] { "java.lang.String", "java.lang.String" });
        assertFalse(has, "Should not have type converter");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                        .convertBodyTo(int.class)
                        .to("mock:a");
            }
        };
    }

}
