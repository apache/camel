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

import java.util.List;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.apache.camel.api.management.JmxSystemPropertyKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * This test verifies the system property to un-select platform mbean server.
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
@DisabledOnOs(OS.AIX)
public class JmxInstrumentationUsingPlatformMBSTest extends JmxInstrumentationUsingPropertiesTest {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        System.setProperty(JmxSystemPropertyKeys.USE_PLATFORM_MBS, "false");
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        System.clearProperty(JmxSystemPropertyKeys.USE_PLATFORM_MBS);
    }

    @Override
    @Test
    public void testMBeanServerType() throws Exception {
        try {
            mbsc.getMBeanInfo(new ObjectName("java.lang:type=OperatingSystem"));
            fail(); // should not get here
        } catch (InstanceNotFoundException e) {
            // expected
        }
    }

    @Override
    protected MBeanServerConnection getMBeanConnection() throws Exception {
        if (mbsc == null) {
            List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);

            for (MBeanServer server : servers) {
                if (domainName.equals(server.getDefaultDomain())) {
                    mbsc = server;
                    break;
                }
            }
        }
        return mbsc;
    }

}
