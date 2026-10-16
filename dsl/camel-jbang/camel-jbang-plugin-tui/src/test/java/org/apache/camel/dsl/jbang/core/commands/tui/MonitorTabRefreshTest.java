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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.tabs.TabsState;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each tab fetches the data only it shows: when it is selected ({@link MonitorTab#onTabSelected()}) and on every data
 * refresh while it is on screen ({@link MonitorTab#refreshWhileVisible()}), with {@code ctx.dataService}.
 */
class MonitorTabRefreshTest {

    private static final List<Long> PIDS = List.of(123L);

    private final MonitorContext ctx = new MonitorContext(
            new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));
    private final DataRefreshService data = mock(DataRefreshService.class);

    @BeforeEach
    void selectAnIntegration() {
        ctx.dataService = data;
        ctx.selectedPid = "123";
        when(data.selectedPidAsList()).thenReturn(PIDS);
        when(data.loadHistoryData(any())).thenReturn(List.of());
    }

    @Test
    void errorsAreReadWhenTheTabOpensAndWhileItIsShown() {
        ErrorsTab tab = new ErrorsTab(ctx);

        tab.onTabSelected();
        tab.refreshWhileVisible();

        verify(data, times(2)).refreshErrorData(PIDS);
    }

    @Test
    void activityIsReadWhenTheTabOpensAndWhileItIsShown() {
        ActivityTab tab = new ActivityTab(ctx);

        tab.onTabSelected();
        tab.refreshWhileVisible();

        verify(data, times(2)).refreshActivityData(PIDS);
    }

    @Test
    void nothingIsReadWithoutASelectedIntegration() {
        when(data.selectedPidAsList()).thenReturn(List.of());
        ctx.selectedPid = null;

        new ErrorsTab(ctx).refreshWhileVisible();
        new HistoryTab(ctx, new AtomicReference<>(List.of()), new HashMap<>()).onTabSelected();

        verify(data, never()).refreshErrorData(any());
        verify(data, never()).loadHistoryData(any());
    }

    @Test
    void historyIsReadWhenTheTabOpensAndAgainOnlyWhenF5AsksForIt() {
        HistoryTab tab = new HistoryTab(ctx, new AtomicReference<>(List.of()), new HashMap<>());

        tab.onTabSelected();
        verify(data, times(1)).loadHistoryData(PIDS);
        verify(data, times(1)).refreshTraceData(PIDS);

        tab.refreshWhileVisible();
        verify(data, times(1)).loadHistoryData(PIDS);
        verify(data, times(2)).refreshTraceData(PIDS);

        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F5));
        tab.refreshWhileVisible();
        verify(data, times(2)).loadHistoryData(PIDS);
    }

    @Test
    void spansAreReadOnceAfterTheTabOpens() {
        SpansTab tab = new SpansTab(ctx, new AtomicReference<>(List.of()));

        tab.refreshWhileVisible();
        verify(data, never()).refreshSpanData();

        tab.onTabSelected();
        tab.refreshWhileVisible();
        tab.refreshWhileVisible();
        verify(data, times(1)).refreshSpanData();
    }

    @Test
    void escOnTheOverviewTabDeselectsTheIntegration() {
        OverviewTab tab = new OverviewTab(ctx, new MetricsCollector(), new HashSet<>(), () -> {
        });

        assertThat(tab.handleEscape()).isTrue();
        assertThat(ctx.selectedPid).isNull();
        assertThat(tab.handleEscape()).as("nothing selected: Esc does nothing more").isFalse();
    }

    @Test
    void theMonitorRefreshesTheTabOnScreen() throws Exception {
        MonitorTab tab = mock(MonitorTab.class);
        TabRegistry registry = mock(TabRegistry.class);
        when(registry.activeTab()).thenReturn(tab);
        CamelMonitor monitor = new CamelMonitor(new CamelJBangMain(), getClass().getClassLoader());
        set(monitor, "tabRegistry", registry);
        set(monitor, "ctx", ctx);
        set(monitor, "dataService", data);

        Method refresh = CamelMonitor.class.getDeclaredMethod("refreshConditionalData");
        refresh.setAccessible(true);
        refresh.invoke(monitor);

        verify(tab).refreshWhileVisible();
    }

    @Test
    void selectingATabTellsIt() {
        when(data.metrics()).thenReturn(new MetricsCollector());
        when(data.traces()).thenReturn(new AtomicReference<>(List.of()));
        when(data.traceFilePositions()).thenReturn(new HashMap<>());
        when(data.otelSpans()).thenReturn(new AtomicReference<>(List.of()));
        when(data.stoppingPids()).thenReturn(new HashSet<>());
        TabRegistry registry = new TabRegistry(new TabsState(TabRegistry.TAB_OVERVIEW));
        registry.initTabs(ctx, data, () -> {
        });
        registry.setCallbacks(new TabRegistry.TabCallbacks() {
            @Override
            public void openMorePopup() {
            }

            @Override
            public void closeMorePopup() {
            }

            @Override
            public void selectMorePopupEntry(int index) {
            }
        });

        registry.handleTabKey(TabRegistry.TAB_ERRORS);

        assertThat(registry.activeTab()).isInstanceOf(ErrorsTab.class);
        verify(data).refreshErrorData(PIDS);
    }

    private static void set(CamelMonitor monitor, String name, Object value) throws Exception {
        Field field = CamelMonitor.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(monitor, value);
    }
}
