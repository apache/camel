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
package org.apache.camel.impl.console;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * ProcessorDevConsole unconditionally dereferences the ManagedCamelContext context plugin (with no null check, in both
 * the action and non-action code paths), which is only available when camel-management is on the classpath - that is
 * not (and should not become) a dependency of this module. So calling the console here would hit that pre-existing,
 * unrelated gap; this test is limited to verifying the console registers correctly.
 */
public class ProcessorDevConsoleTest extends ContextTestSupport {

    @Test
    public void testProcessorConsoleResolves() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("processor");
        Assertions.assertNotNull(con);
        Assertions.assertEquals("camel", con.getGroup());
        Assertions.assertEquals("processor", con.getId());
    }
}
