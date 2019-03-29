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

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.junit.Test;

public class ManagedCamelContextNewProxyTest extends ManagementTestSupport {

    @Test
    public void testNewProxy() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=context,name=\"camel-1\"");

        ManagedCamelContextMBean proxy = JMX.newMBeanProxy(mbeanServer, on, ManagedCamelContextMBean.class);
        assertNotNull(proxy);
    }
}
