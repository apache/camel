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

import dev.tamboui.widgets.tabs.TabsState;

/**
 * Owns all tab instances, tab index constants, and tab navigation logic. Extracted from {@link CamelMonitor} to reduce
 * class size.
 */
class TabRegistry {

    // Tab indices
    static final int TAB_OVERVIEW = 0;
    static final int TAB_LOG = 1;
    static final int TAB_DIAGRAM = 2;
    static final int TAB_ROUTES = 3;
    static final int TAB_ENDPOINTS = 4;
    static final int TAB_HTTP = 5;
    static final int TAB_HEALTH = 6;
    static final int TAB_HISTORY = 7;
    static final int TAB_ERRORS = 8;
    static final int TAB_MORE = 9;

    static final int NUM_TABS = 10;

    /**
     * Callbacks for operations that remain in {@link CamelMonitor} or other collaborators.
     */
    interface TabCallbacks {
        void refreshLogData();

        void refreshHistoryData(List<Long> pids);

        void refreshTraceData(List<Long> pids);

        void refreshErrorData(List<Long> pids);

        void openMorePopup();

        void closeMorePopup();

        void selectMorePopupEntry(int index);
    }

    private final TabsState tabsState;
    private TabCallbacks callbacks;

    // Tab instances
    private LogTab logTab;
    private DiagramTab diagramTab;
    private RoutesTab routesTab;
    private ConsumersTab consumersTab;
    private EndpointsTab endpointsTab;
    private HttpTab httpTab;
    private HealthTab healthTab;
    private HistoryTab historyTab;
    private CircuitBreakerTab circuitBreakerTab;
    private ErrorsTab errorsTab;
    private MetricsTab metricsTab;
    private StartupTab startupTab;
    private ConfigurationTab configurationTab;
    private BeansTab beansTab;
    private BrowseTab browseTab;
    private ClasspathTab classpathTab;
    private InflightTab inflightTab;
    private MemoryTab memoryTab;
    private ThreadsTab threadsTab;
    private SpansTab spansTab;
    private ProcessTab processTab;
    private OverviewTab overviewTab;
    private DataSourceTab dataSourceTab;
    private SqlQueryTab sqlQueryTab;

    private MonitorTab activeMoreTab;

    TabRegistry(TabsState tabsState) {
        this.tabsState = tabsState;
    }

    void setCallbacks(TabCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    void initTabs(MonitorContext ctx, DataRefreshService dataService, Runnable resetIntegrationTabState) {
        logTab = new LogTab(ctx);
        diagramTab = new DiagramTab(ctx);
        routesTab = new RoutesTab(ctx);
        consumersTab = new ConsumersTab(ctx);
        dataSourceTab = new DataSourceTab(ctx);
        sqlQueryTab = new SqlQueryTab(ctx);
        endpointsTab = new EndpointsTab(ctx, dataService.metrics());
        httpTab = new HttpTab(ctx);
        healthTab = new HealthTab(ctx);
        historyTab = new HistoryTab(ctx, dataService.traces(), dataService.traceFilePositions());
        circuitBreakerTab = new CircuitBreakerTab(ctx, dataService.metrics());
        errorsTab = new ErrorsTab(ctx);
        metricsTab = new MetricsTab(ctx);
        startupTab = new StartupTab(ctx);
        configurationTab = new ConfigurationTab(ctx);
        beansTab = new BeansTab(ctx);
        browseTab = new BrowseTab(ctx);
        classpathTab = new ClasspathTab(ctx);
        inflightTab = new InflightTab(ctx);
        memoryTab = new MemoryTab(ctx, dataService.metrics());
        threadsTab = new ThreadsTab(ctx);
        spansTab = new SpansTab(ctx, dataService.otelSpans());
        processTab = new ProcessTab(ctx);
        overviewTab = new OverviewTab(
                ctx, dataService.metrics(), dataService.stoppingPids(),
                resetIntegrationTabState);
    }

    // ---- Tab access ----

    MonitorTab activeTab() {
        return switch (tabsState.selected()) {
            case TAB_OVERVIEW -> overviewTab;
            case TAB_LOG -> logTab;
            case TAB_DIAGRAM -> diagramTab;
            case TAB_ROUTES -> routesTab;
            case TAB_ENDPOINTS -> endpointsTab;
            case TAB_HEALTH -> healthTab;
            case TAB_HISTORY -> historyTab;
            case TAB_HTTP -> httpTab;
            case TAB_ERRORS -> errorsTab;
            case TAB_MORE -> activeMoreTab;
            default -> null;
        };
    }

    MonitorTab getActiveMoreTab() {
        return activeMoreTab;
    }

    int selectedTabIndex() {
        return tabsState.selected();
    }

    // ---- Navigation ----

    boolean handleTabKey(int tab, MonitorContext ctx, DataRefreshService dataService) {
        if (tab != TAB_OVERVIEW) {
            overviewTab.selectCurrentIntegration();
            routesTab.preloadDiagram();
            diagramTab.preloadDiagram();
        }
        if (tab == TAB_LOG) {
            callbacks.refreshLogData();
            logTab.onTabSelected();
        }
        if (tab == TAB_ROUTES && routesTab != null && routesTab.isShowDiagram()) {
            routesTab.closeDiagram();
        }
        if (tab == TAB_DIAGRAM) {
            diagramTab.onTabSelected();
        }
        if (tab == TAB_HISTORY && ctx.selectedPid != null) {
            try {
                long pid = Long.parseLong(ctx.selectedPid);
                historyTab.historyEntries = dataService.loadHistoryData(List.of(pid));
                dataService.refreshTraceData(List.of(pid));
            } catch (NumberFormatException e) {
                // ignore
            }
            historyTab.onTabSelected();
        }
        if (tab == TAB_ERRORS && ctx.selectedPid != null) {
            try {
                long pid = Long.parseLong(ctx.selectedPid);
                dataService.refreshErrorData(List.of(pid));
            } catch (NumberFormatException e) {
                // ignore
            }
            errorsTab.onTabSelected();
        }
        if (tab == TAB_MORE) {
            callbacks.openMorePopup();
            return true;
        }
        callbacks.closeMorePopup();
        tabsState.select(tab);
        return true;
    }

    void selectMoreTab(int index) {
        callbacks.selectMorePopupEntry(index);
        activeMoreTab = switch (index) {
            case 0 -> beansTab;
            case 1 -> browseTab;
            case 2 -> circuitBreakerTab;
            case 3 -> classpathTab;
            case 4 -> configurationTab;
            case 5 -> consumersTab;
            case 6 -> dataSourceTab;
            case 7 -> inflightTab;
            case 8 -> memoryTab;
            case 9 -> metricsTab;
            case 10 -> sqlQueryTab;
            case 11 -> spansTab;
            case 12 -> processTab;
            case 13 -> startupTab;
            case 14 -> threadsTab;
            default -> null;
        };
        if (activeMoreTab != null) {
            overviewTab.selectCurrentIntegration();
            tabsState.select(TAB_MORE);
            activeMoreTab.onTabSelected();
        }
    }

    void resetIntegrationTabState(DataRefreshService dataService, FilesBrowser filesBrowser) {
        diagramTab.onIntegrationChanged();
        routesTab.onIntegrationChanged();
        httpTab.onIntegrationChanged();
        logTab.onIntegrationChanged();
        historyTab.onIntegrationChanged();
        beansTab.onIntegrationChanged();
        browseTab.onIntegrationChanged();
        threadsTab.onIntegrationChanged();
        startupTab.onIntegrationChanged();
        configurationTab.onIntegrationChanged();
        consumersTab.onIntegrationChanged();
        dataSourceTab.onIntegrationChanged();
        sqlQueryTab.onIntegrationChanged();
        circuitBreakerTab.onIntegrationChanged();
        inflightTab.onIntegrationChanged();
        spansTab.onIntegrationChanged();
        processTab.onIntegrationChanged();
        dataService.otelSpans().set(List.of());

        filesBrowser.reset();

        // Preload diagram data in background so it's ready when the user switches tabs
        routesTab.preloadDiagram();
        diagramTab.preloadDiagram();
    }

    void navigateUp() {
        activeTab().navigateUp();
    }

    void navigateDown() {
        activeTab().navigateDown();
    }

    // ---- Typed tab accessors ----

    LogTab logTab() {
        return logTab;
    }

    DiagramTab diagramTab() {
        return diagramTab;
    }

    RoutesTab routesTab() {
        return routesTab;
    }

    HttpTab httpTab() {
        return httpTab;
    }

    HealthTab healthTab() {
        return healthTab;
    }

    HistoryTab historyTab() {
        return historyTab;
    }

    ErrorsTab errorsTab() {
        return errorsTab;
    }

    SpansTab spansTab() {
        return spansTab;
    }

    OverviewTab overviewTab() {
        return overviewTab;
    }

    SqlQueryTab sqlQueryTab() {
        return sqlQueryTab;
    }
}
