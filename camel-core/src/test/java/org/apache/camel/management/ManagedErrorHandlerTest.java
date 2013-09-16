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

import java.util.Iterator;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedErrorHandlerTest extends ManagementTestSupport {

    public void testManagedErrorHandler() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=errorhandlers,*"), null);
        // there should only be 2 error handler types as route 1 and route 3 uses the same default error handler
        assertEquals(2, set.size());

        Iterator<ObjectName> it = set.iterator();
        ObjectName on1 = it.next();
        ObjectName on2 = it.next();

        String name1 = on1.getCanonicalName();
        String name2 = on2.getCanonicalName();

        assertTrue("Should be a default error handler", name1.contains("CamelDefaultErrorHandlerBuilder") || name2.contains("CamelDefaultErrorHandlerBuilder"));
        assertTrue("Should be a dead letter error handler", name1.contains("DeadLetterChannelBuilder") || name2.contains("DeadLetterChannelBuilder"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").to("mock:foo");

                from("direct:bar").errorHandler(deadLetterChannel("mock:dead")).to("mock:bar");

                from("direct:baz").to("mock:baz");
            }
        };
    }
}
