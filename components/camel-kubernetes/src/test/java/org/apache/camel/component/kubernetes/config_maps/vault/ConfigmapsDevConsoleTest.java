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
package org.apache.camel.component.kubernetes.config_maps.vault;

import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ConfigmapsDevConsole unconditionally dereferences the Kubernetes vault configuration's secrets list (a pre-existing
 * bug, preserved as-is by this migration), so calling it without a real Kubernetes vault configuration would NPE; this
 * test is limited to verifying the console registers correctly.
 */
public class ConfigmapsDevConsoleTest extends CamelTestSupport {

    @Test
    public void testConfigmapsConsoleResolves() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("kubernetes-configmaps");
        assertNotNull(con);
        assertEquals("camel", con.getGroup());
        assertEquals("kubernetes-configmaps", con.getId());
    }
}
