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

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.FileWatcherReloadStrategy;

/**
 * @version 
 */
public class ManagedFileWatcherReloadStrategyTest extends ManagementTestSupport {

    public void testReloadStrategy() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=services,name=FileWatcherReloadStrategy");
        assertTrue(mbeanServer.isRegistered(on));

        String folder = (String) mbeanServer.getAttribute(on, "Folder");
        assertEquals("target/dummy", folder);

        Boolean running = (Boolean) mbeanServer.getAttribute(on, "Running");
        assertTrue(running);

        Integer reload = (Integer) mbeanServer.getAttribute(on, "ReloadCounter");
        assertEquals(0, reload.intValue());

        Integer failed = (Integer) mbeanServer.getAttribute(on, "FailedCounter");
        assertEquals(0, failed.intValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // directory must exists for the watcher to be able to run
                deleteDirectory("target/dummy");
                createDirectory("target/dummy");

                // add reload strategy
                context.setReloadStrategy(new FileWatcherReloadStrategy("target/dummy"));

                from("direct:start")
                    .to("mock:result");
            }
        };
    }

}
