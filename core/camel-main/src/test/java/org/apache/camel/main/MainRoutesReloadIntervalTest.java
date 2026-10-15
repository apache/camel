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

import java.nio.file.Path;

import org.apache.camel.support.RouteWatcherReloadStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MainRoutesReloadIntervalTest {

    @TempDir
    Path dir;

    @Test
    public void shouldScanEveryTwoSecondsByDefault() {
        assertEquals(2000, scanInterval(null));
    }

    @Test
    public void shouldScanAtTheConfiguredInterval() {
        assertEquals(500, scanInterval("500"));
    }

    @Test
    public void shouldScanAtTheIntervalOfAnOptionalKey() {
        // the camel_run AI tool passes the optional key, which a Camel without the option ignores
        assertEquals(500, scanInterval("camel.main.?routesReloadInterval", "500"));
    }

    @Test
    public void shouldKeepTheDefaultForAnIntervalOfZero() {
        // zero would scan without a pause
        assertEquals(2000, scanInterval("0"));
    }

    private long scanInterval(String interval) {
        return scanInterval("camel.main.routesReloadInterval", interval);
    }

    private long scanInterval(String key, String interval) {
        Main main = new Main();
        main.configure().withRoutesReloadEnabled(true).withRoutesReloadDirectory(dir.toString());
        if (interval != null) {
            main.addInitialProperty(key, interval);
        }
        main.start();
        try {
            RouteWatcherReloadStrategy reloader = main.getCamelContext().hasService(RouteWatcherReloadStrategy.class);
            assertNotNull(reloader, "routesReloadEnabled=true should add the reload strategy");
            return reloader.getPollTimeout();
        } finally {
            main.stop();
        }
    }
}
