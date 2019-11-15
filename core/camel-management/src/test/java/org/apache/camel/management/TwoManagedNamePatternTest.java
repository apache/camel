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

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Test;

public class TwoManagedNamePatternTest extends TestSupport {

    private CamelContext camel1;
    private CamelContext camel2;

    protected CamelContext createCamelContext(String name, String pattern) throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.setName(name);
        context.getManagementNameStrategy().setNamePattern(pattern);
        return context;
    }

    @Test
    public void testManagedNamePattern() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        camel1 = createCamelContext("foo", "aaa-#name#");
        camel2 = createCamelContext("bar", "bbb-#name#");

        camel1.start();
        camel2.start();

        MBeanServer mbeanServer = camel1.getManagementStrategy().getManagementAgent().getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=aaa-foo,type=context,name=\"foo\"");
        assertTrue("Should be registered", mbeanServer.isRegistered(on));

        ObjectName on2 = ObjectName.getInstance("org.apache.camel:context=bbb-bar,type=context,name=\"bar\"");
        assertTrue("Should be registered", mbeanServer.isRegistered(on2));

        camel1.stop();
        camel2.stop();

        assertFalse("Should be unregistered", mbeanServer.isRegistered(on));
        assertFalse("Should be unregistered", mbeanServer.isRegistered(on2));
    }

    @Override
    @After
    public void tearDown() throws Exception {
        if (camel1 != null) {
            camel1.stop();
        }
        if (camel2 != null) {
            camel2.stop();
        }
        super.tearDown();
    }

}
