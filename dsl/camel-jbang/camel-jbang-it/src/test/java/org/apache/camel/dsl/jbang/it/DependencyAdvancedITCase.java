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
package org.apache.camel.dsl.jbang.it;

import java.io.IOException;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

@DisabledOnOs(WINDOWS)
public class DependencyAdvancedITCase extends JBangTestSupport {

    @Test
    public void testDependencyList() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        // dependency list resolves the Camel artifacts needed for a route file
        checkCommandOutputs(
                String.format("dependency list %s/route2.yaml", mountPoint()),
                "camel-main");
    }

    @Test
    public void testDependencyListShowsComponent() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        // the timer component is referenced in route2.yaml so it must appear in the dependency list
        checkCommandOutputs(
                String.format("dependency list %s/route2.yaml", mountPoint()),
                "camel-timer");
    }
}
