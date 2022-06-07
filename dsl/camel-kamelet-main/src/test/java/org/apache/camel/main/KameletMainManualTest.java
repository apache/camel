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
package org.apache.camel.main;

import org.apache.camel.StartupSummaryLevel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Manual test")
public class KameletMainManualTest {

    @Test
    public void testKameletMain() throws Exception {
        KameletMain main = new KameletMain();
        main.setDownload(true);
        main.configure().withDurationMaxSeconds(5);
        main.configure().withRoutesIncludePattern("file:src/test/resources/my-route.yaml");

        main.run();
    }

    @Test
    public void testReload() throws Exception {
        KameletMain main = new KameletMain();
        main.configure().setStartupSummaryLevel(StartupSummaryLevel.Verbose);
        main.setDownload(true);
        main.configure().withDurationMaxMessages(10);
        main.configure().withDurationMaxAction("stop");
        main.configure().withRoutesIncludePattern("file:src/test/resources/my-route.yaml");
        main.configure().withRoutesReloadEnabled(true);
        main.configure().withRoutesReloadDirectory("src/test/resources");
        main.configure().withRoutesReloadPattern("my-route.yaml");

        main.run();
    }

    @Test
    public void testReloadCamelK() throws Exception {
        KameletMain main = new KameletMain();
        main.setDownload(true);
        main.configure().withShutdownTimeout(5);
        main.configure().withDurationMaxMessages(10);
        main.configure().withDurationMaxAction("stop");
        main.configure().withRoutesIncludePattern("file:src/test/resources/my-camel-k.yaml");
        main.configure().withRoutesReloadEnabled(true);
        main.configure().withRoutesReloadDirectory("src/test/resources");
        main.configure().withRoutesReloadPattern("my-camel-k.yaml");

        main.run();
    }
}
