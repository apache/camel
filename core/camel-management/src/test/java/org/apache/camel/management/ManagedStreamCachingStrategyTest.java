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

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.Test;

import static org.apache.camel.util.FileUtil.normalizePath;

public class ManagedStreamCachingStrategyTest extends ManagementTestSupport {

    @Test
    public void testStreamCachingStrategy() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=myCamel,type=services,*");

        // number of services
        Set<ObjectName> names = mbeanServer.queryNames(on, null);
        ObjectName name = null;
        for (ObjectName service : names) {
            if (service.toString().contains("DefaultStreamCachingStrategy")) {
                name = service;
                break;
            }
        }
        assertNotNull("Cannot find DefaultStreamCachingStrategy", name);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(name, "Enabled");
        assertEquals(Boolean.TRUE, enabled);

        String dir = (String) mbeanServer.getAttribute(name, "SpoolDirectory");
        assertEquals(normalizePath("target/data/cachedir/myCamel"), normalizePath(dir));

        Long threshold = (Long) mbeanServer.getAttribute(name, "SpoolThreshold");
        assertEquals(StreamCache.DEFAULT_SPOOL_THRESHOLD, threshold.longValue());

        Integer size = (Integer) mbeanServer.getAttribute(name, "BufferSize");
        assertEquals(IOHelper.DEFAULT_BUFFER_SIZE, size.intValue());

        Long counter = (Long) mbeanServer.getAttribute(name, "CacheMemoryCounter");
        assertEquals(0, counter.longValue());

        counter = (Long) mbeanServer.getAttribute(name, "CacheSpoolCounter");
        assertEquals(0, counter.longValue());

        Long cacheSize = (Long) mbeanServer.getAttribute(name, "CacheMemorySize");
        assertEquals(0, cacheSize.longValue());

        cacheSize = (Long) mbeanServer.getAttribute(name, "CacheSpoolSize");
        assertEquals(0, cacheSize.longValue());

        String cipher = (String) mbeanServer.getAttribute(name, "SpoolCipher");
        assertNull(cipher);

        Boolean remove = (Boolean) mbeanServer.getAttribute(name, "RemoveSpoolDirectoryWhenStopping");
        assertEquals(Boolean.TRUE, remove);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                DefaultCamelContext dcc = (DefaultCamelContext) context;
                dcc.setName("myCamel");

                context.setStreamCaching(true);
                context.getStreamCachingStrategy().setSpoolDirectory("target/data/cachedir/#name#/");

                from("direct:start").routeId("foo")
                    .convertBodyTo(int.class)
                    .to("mock:a");
            }
        };
    }

}
