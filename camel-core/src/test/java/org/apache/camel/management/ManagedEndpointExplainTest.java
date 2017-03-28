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
import javax.management.openmbean.TabularData;

import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedEndpointExplainTest extends ManagementTestSupport {

    public void testManageEndpointExplain() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"seda://test\"");
        assertTrue(mbeanServer.isRegistered(on));

        on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://result\"");
        assertTrue(mbeanServer.isRegistered(on));

        on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"log://foo\\?groupDelay=2000&groupSize=5&level=WARN\"");
        assertTrue(mbeanServer.isRegistered(on));

        // there should be 3 parameters + 1 path = 4
        TabularData data = (TabularData) mbeanServer.invoke(on, "explain", new Object[]{false}, new String[]{"boolean"});
        assertEquals(4, data.size());

        data = (TabularData) mbeanServer.invoke(on, "explain", new Object[]{true}, new String[]{"boolean"});
        assertEquals(27, data.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:test")
                    .to("log:foo?groupDelay=2000&groupSize=5&level=WARN")
                    .to("mock:result");
            }
        };
    }

}