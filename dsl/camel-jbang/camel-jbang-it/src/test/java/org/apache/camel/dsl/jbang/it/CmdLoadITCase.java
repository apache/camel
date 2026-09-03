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

public class CmdLoadITCase extends JBangTestSupport {
    @Test
    public void testCmdLoad() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        copyResourceInDataFolder(TestResources.DIR_ROUTE);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkCommandOutputs(String.format("cmd load --source=%s/FromDirectoryRoute.java", mountPoint()),
                "Successfully loaded 1 source files");
        //check both routes are running in one app
        checkCommandOutputsPattern("ps",
                "INFLIGHT\\s+\\d+\\s+route2\\s+1\\/1\\s+Running\\s+(\\d+m)?\\d+s\\s+\\d+\\s+0\\s+0\\s*$");
        checkCommandOutputsPattern("get route",
                "route2\\s+route1.*yaml.*Started.*\\s+.*route2\\s+route2.*Started");
    }
}
