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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpFacadeAwaitTableDataTest {

    /** A tab whose data appears only some polls after the load was requested, like the classpath tab. */
    private static class OnDemandTab implements MonitorTab {

        final AtomicInteger loadRequests = new AtomicInteger();
        final AtomicInteger reads = new AtomicInteger();
        int readsUntilData = Integer.MAX_VALUE;
        String error;

        @Override
        public boolean handleKeyEvent(KeyEvent ke) {
            return false;
        }

        @Override
        public void render(Frame frame, Rect area) {
        }

        @Override
        public String description() {
            return "test";
        }

        @Override
        public void renderFooter(List<Span> spans) {
        }

        @Override
        public JsonObject getTableDataAsJson() {
            if (reads.incrementAndGet() >= readsUntilData) {
                JsonObject data = new JsonObject();
                data.put("tab", "Test");
                return data;
            }
            return null;
        }

        @Override
        public boolean ensureDataLoaded() {
            loadRequests.incrementAndGet();
            return true;
        }

        @Override
        public String dataLoadError() {
            return error;
        }
    }

    @Test
    void startsTheLoadAndWaitsForTheData() {
        OnDemandTab tab = new OnDemandTab();
        tab.readsUntilData = 3;

        JsonObject data = McpFacade.awaitTableData(tab, 5_000);

        assertNotNull(data);
        assertEquals(1, tab.loadRequests.get());
        assertTrue(tab.reads.get() >= 3);
    }

    @Test
    void stopsWaitingWhenTheLoadReportsAnErrorOrEmptyResult() {
        OnDemandTab tab = new OnDemandTab();
        tab.error = "No response from integration";
        long start = System.currentTimeMillis();

        assertNull(McpFacade.awaitTableData(tab, 5_000));

        assertTrue(System.currentTimeMillis() - start < 2_000, "must not wait for the whole timeout");
        assertEquals(1, tab.loadRequests.get());
    }

    @Test
    void givesUpAfterTheTimeout() {
        OnDemandTab tab = new OnDemandTab();

        assertNull(McpFacade.awaitTableData(tab, 300));
        assertEquals(1, tab.loadRequests.get());
    }

    @Test
    void tabsWithoutOnDemandLoadingAreReadOnce() {
        OnDemandTab tab = new OnDemandTab() {
            @Override
            public boolean ensureDataLoaded() {
                return false;
            }
        };

        assertNull(McpFacade.awaitTableData(tab, 5_000));
        assertEquals(1, tab.reads.get());
    }
}
