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
package org.apache.camel.management;

import java.util.Set;
import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedTypeConverterRegistryTest extends ManagementTestSupport {

    public void testTypeConverterRegistry() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:a").expectedMessageCount(2);

        template.sendBody("direct:start", "3");
        template.sendBody("direct:start", "7");

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=services,*");

        // number of services
        Set<ObjectName> names = mbeanServer.queryNames(on, null);
        ObjectName name = null;
        for (ObjectName service : names) {
            if (service.toString().contains("DefaultTypeConverter")) {
                name = service;
                break;
            }
        }
        assertNotNull("Cannot find DefaultTypeConverter", name);

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
        Long coreHit = (Long) mbeanServer.getAttribute(name, "BaseHitCounter");
        assertEquals(1, coreHit.intValue());
        failed = (Long) mbeanServer.getAttribute(name, "FailedCounter");
        assertEquals(0, failed.intValue());
        miss = (Long) mbeanServer.getAttribute(name, "MissCounter");
        assertEquals(0, miss.intValue());

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
        assertEquals(0, miss.intValue());

        // reset
        mbeanServer.invoke(name, "resetTypeConversionCounters", null, null);

        failed = (Long) mbeanServer.getAttribute(name, "FailedCounter");
        assertEquals(0, failed.intValue());
        miss = (Long) mbeanServer.getAttribute(name, "MissCounter");
        assertEquals(0, miss.intValue());

        // we have more than 150 converters out of the box
        Integer converters = (Integer) mbeanServer.getAttribute(name, "NumberOfTypeConverters");
        assertTrue("Should be more than 150 converters, was: " + converters, converters >= 150);

        Boolean has = (Boolean) mbeanServer.invoke(name, "hasTypeConverter", new Object[]{"String", "java.io.InputStream"}, new String[]{"java.lang.String", "java.lang.String"});
        assertTrue("Should have type converter", has.booleanValue());

        has = (Boolean) mbeanServer.invoke(name, "hasTypeConverter", new Object[]{"java.math.BigInteger", "int"}, new String[]{"java.lang.String", "java.lang.String"});
        assertFalse("Should not have type converter", has.booleanValue());

        // we have more than 150 converters out of the box
        TabularData data = (TabularData) mbeanServer.invoke(name, "listTypeConverters", null, null);
        assertTrue("Should be more than 150 converters, was: " + data.size(), data.size() >= 150);
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
