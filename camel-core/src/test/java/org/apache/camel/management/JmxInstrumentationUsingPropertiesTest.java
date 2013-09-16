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

import javax.management.ObjectName;

/**
 * This module contains test cases that verifies jmx system property uses.
 *
 * @version 
 */
public class JmxInstrumentationUsingPropertiesTest extends JmxInstrumentationUsingDefaultsTest {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected void setUp() throws Exception {
        domainName = "org.apache.camel-properties";
        System.setProperty(JmxSystemPropertyKeys.DOMAIN, domainName);
        System.setProperty(JmxSystemPropertyKeys.MBEAN_DOMAIN, domainName);

        super.setUp();
    }

    public void testMBeanServerType() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // default is platform mbs
        assertNotNull(mbsc.getMBeanInfo(new ObjectName("java.lang:type=OperatingSystem")));        
    }
}
