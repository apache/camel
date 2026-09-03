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

public class ProcessorITCase extends JBangTestSupport {
    @Test
    public void testDisableEIP() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkCommandOutputsPattern("get processor", "route1\\s+timer:\\/\\/yaml.*\\s+Started");
        execute("cmd disable-processor --id=route1");
        checkCommandOutputsPattern("get processor", "log1\\s+log\\s+Disabled");
    }

    @Test
    public void testReenableEIP() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkCommandOutputsPattern("get processor", "route1\\s+timer:\\/\\/yaml.*\\s+Started");
        execute("cmd disable-processor --id=route1");
        checkCommandOutputsPattern("get processor", "log1\\s+log\\s+Disabled");
        execute("cmd enable-processor --id=route1");
        checkCommandOutputsPattern("get processor", "log1\\s+log\\s+Started");
    }
}
