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

import org.apache.camel.api.management.JmxSystemPropertyKeys;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
@DisabledOnOs(OS.AIX)
public class ManagedNamePatternJvmSystemPropertyTest extends ManagementTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        System.setProperty(JmxSystemPropertyKeys.MANAGEMENT_NAME_PATTERN, "cool-#name#");
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        System.clearProperty(JmxSystemPropertyKeys.MANAGEMENT_NAME_PATTERN);
        super.tearDown();
    }

    @Test
    public void testManagedNamePattern() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        assertEquals("cool-" + context.getName(), context.getManagementName());

        ObjectName on = getContextObjectName();
        assertTrue(mbeanServer.isRegistered(on), "Should be registered");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        };
    }
}
