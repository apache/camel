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

import java.util.ArrayList;
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
    static final int TAB_ACTIVITY = 2;
    static final int TAB_DIAGRAM = 3;
    static final int TAB_ROUTES = 4;
    static final int TAB_ENDPOINTS = 5;
    static final int TAB_HTTP = 6;
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

        void refreshActivityData(List<Long> pids);

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
    private ActivityTab activityTab;
    private MetricsTab metricsTab;
    private StartupTab startupTab;
    private ConfigurationTab configurationTab;
    private BeansTab beansTab;
    private BrowseTab browseTab;
    private ClasspathTab classpathTab;
    private MavenDependenciesTab mavenDependenciesTab;
    private CveAuditTab cveAuditTab;
    private InflightTab inflightTab;
    private MemoryTab memoryTab;
    private HeapHistogramTab heapHistogramTab;
    private MemoryLeakTab memoryLeakTab;
    private ThreadsTab threadsTab;
    private SpansTab spansTab;
    private ProcessTab processTab;
    private OverviewTab overviewTab;
    private DataSourceTab dataSourceTab;
    private SqlQueryTab sqlQueryTab;
    private SqlTraceTab sqlTraceTab;

    private MonitorTab activeMoreTab;
    private List<MoreTab> moreTabs;

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
        heapHistogramTab = new HeapHistogramTab(ctx);
        memoryLeakTab = new MemoryLeakTab(ctx);
        sqlQueryTab = new SqlQueryTab(ctx);
        sqlTraceTab = new SqlTraceTab(ctx);
        endpointsTab = new EndpointsTab(ctx, dataService.metrics());
        httpTab = new HttpTab(ctx);
        healthTab = new HealthTab(ctx);
        historyTab = new HistoryTab(ctx, dataService.traces(), dataService.traceFilePositions());
        circuitBreakerTab = new CircuitBreakerTab(ctx, dataService.metrics());
        errorsTab = new ErrorsTab(ctx);
        activityTab = new ActivityTab(ctx);
        metricsTab = new MetricsTab(ctx);
        startupTab = new StartupTab(ctx);
        configurationTab = new ConfigurationTab(ctx);
        beansTab = new BeansTab(ctx);
        browseTab = new BrowseTab(ctx);
        classpathTab = new ClasspathTab(ctx);
        mavenDependenciesTab = new MavenDependenciesTab(ctx);
        cveAuditTab = new CveAuditTab(ctx);
        inflightTab = new InflightTab(ctx);
        memoryTab = new MemoryTab(ctx, dataService.metrics());
        threadsTab = new ThreadsTab(ctx);
        spansTab = new SpansTab(ctx, dataService.otelSpans());
        processTab = new ProcessTab(ctx);
        overviewTab = new OverviewTab(
                ctx, dataService.metrics(), dataService.stoppingPids(),
                resetIntegrationTabState);

        sqlTraceTab.setEditSqlAction(sql -> {
            selectMoreTab(moreTabIndex("SQL Query"));
            sqlQueryTab.setInputValue("sql", sql);
        });

        // Single source of truth for the More submenu: icon, programmatic name, mnemonic label and tab instance.
        // Order defines the More popup index used by selectMoreTab(int).
        moreTabs = List.of(
                new MoreTab(TuiIcons.TAB_BEANS, "Beans", "&Beans", beansTab),
                new MoreTab(TuiIcons.TAB_BROWSE, "Browse", "Bro&wse", browseTab),
                new MoreTab(TuiIcons.TAB_CIRCUIT_BREAKER, "Circuit Breaker", "&Circuit Breaker", circuitBreakerTab),
                new MoreTab(TuiIcons.TAB_CLASSPATH, "Classpath", "Cl&asspath", classpathTab),
                new MoreTab(TuiIcons.TAB_CONFIGURATION, "Configuration", "Confi&guration", configurationTab),
                new MoreTab(TuiIcons.TAB_CONSUMERS, "Consumers", "Co&nsumers", consumersTab),
                new MoreTab(TuiIcons.TAB_CVE_AUDIT, "CVE Audit", "C&VE Audit", cveAuditTab),
                new MoreTab(TuiIcons.TAB_HEALTH, "Health", "H&ealth", healthTab),
                new MoreTab(TuiIcons.TAB_HEAP, "Heap Histogram", "&Heap Histogram", heapHistogramTab),
                new MoreTab(TuiIcons.TAB_INFLIGHT, "Inflight", "In&flight", inflightTab),
                new MoreTab(TuiIcons.TAB_DATASOURCE, "JDBC DataSource", "&JDBC DataSource", dataSourceTab),
                new MoreTab(TuiIcons.TAB_MAVEN_DEPENDENCIES, "Maven Dependencies", "Maven &Dependencies", mavenDependenciesTab),
                new MoreTab(TuiIcons.TAB_MEMORY, "Memory", "&Memory", memoryTab),
                new MoreTab(TuiIcons.TAB_MEMORY_LEAK, "Memory Leak", "Memory Lea&k", memoryLeakTab),
                new MoreTab(TuiIcons.TAB_METRICS, "Metrics", "Metr&ics", metricsTab),
                new MoreTab(TuiIcons.TAB_SQL_QUERY, "SQL Query", "S&QL Query", sqlQueryTab),
                new MoreTab(TuiIcons.TAB_SQL_TRACE, "SQL Trace", "SQL T&race", sqlTraceTab),
                new MoreTab(TuiIcons.TAB_SPANS, "Spans", "&OTel Spans", spansTab),
                new MoreTab(TuiIcons.TAB_PROCESS, "Process", "&Process", processTab),
                new MoreTab(TuiIcons.TAB_STARTUP, "Startup", "&Startup", startupTab),
                new MoreTab(TuiIcons.TAB_THREADS, "Threads", "&Threads", threadsTab));
    }

    // ---- Tab access ----

    MonitorTab activeTab() {
        return switch (tabsState.selected()) {
            case TAB_OVERVIEW -> overviewTab;
            case TAB_LOG -> logTab;
            case TAB_ACTIVITY -> activityTab;
            case TAB_DIAGRAM -> diagramTab;
            case TAB_ROUTES -> routesTab;
            case TAB_ENDPOINTS -> endpointsTab;
            case TAB_HTTP -> httpTab;
            case TAB_HISTORY -> historyTab;
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
        if (tab == TAB_ACTIVITY && ctx.selectedPid != null) {
            try {
                long pid = Long.parseLong(ctx.selectedPid);
                callbacks.refreshActivityData(List.of(pid));
            } catch (NumberFormatException e) {
                // ignore
            }
            activityTab.onTabSelected();
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
        activeMoreTab = index >= 0 && index < moreTabs.size() ? moreTabs.get(index).tab() : null;
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
        for (MoreTab mt : moreTabs) {
            mt.tab().onIntegrationChanged();
        }
        dataService.otelSpans().set(List.of());

        filesBrowser.reset();
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

    ActivityTab activityTab() {
        return activityTab;
    }

    BeansTab beansTab() {
        return beansTab;
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

    ClasspathTab classpathTab() {
        return classpathTab;
    }

    MavenDependenciesTab mavenDependenciesTab() {
        return mavenDependenciesTab;
    }

    CveAuditTab cveAuditTab() {
        return cveAuditTab;
    }

    // ---- Tab entries for Go-to and MCP ----

    record TabEntry(String icon, String name, String description, String shortcut, int tabIndex, int moreIndex) {
    }

    /**
     * A "More" submenu tab. Bundles its icon, programmatic {@code name} (used for tab lookup and the Go to… popup),
     * popup {@code label} carrying a {@value TuiIcons#MNEMONIC_MARKER} shortcut marker, and the tab instance. The
     * shortcut letter and its highlight offset are derived from {@code label} via
     * {@link TuiIcons#mnemonicIndex(String)}, so there is no separate index or shortcut list to keep aligned.
     */
    record MoreTab(String icon, String name, String label, MonitorTab tab) {

        MoreTab {
            int i = TuiIcons.mnemonicIndex(label);
            if (i < 0 || i >= TuiIcons.stripMnemonic(label).length()) {
                throw new IllegalArgumentException(
                        "label must contain a '" + TuiIcons.MNEMONIC_MARKER + "' marker before a letter: " + label);
            }
        }

        String displayName() {
            return TuiIcons.stripMnemonic(label);
        }

        int mnemonicIndex() {
            return TuiIcons.mnemonicIndex(label);
        }

        char shortcut() {
            return Character.toUpperCase(displayName().charAt(mnemonicIndex()));
        }
    }

    List<MoreTab> moreTabs() {
        return moreTabs;
    }

    /** Position of the More tab with the given programmatic {@link MoreTab#name() name}, or -1 when absent. */
    int moreTabIndex(String name) {
        for (int i = 0; i < moreTabs.size(); i++) {
            if (moreTabs.get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    List<TabEntry> allTabEntries() {
        List<TabEntry> entries = new ArrayList<>();
        entries.add(new TabEntry(icon(TAB_OVERVIEW), "Overview", overviewTab.description(), "1", TAB_OVERVIEW, -1));
        entries.add(new TabEntry(icon(TAB_LOG), "Log", logTab.description(), "2", TAB_LOG, -1));
        entries.add(new TabEntry(icon(TAB_ACTIVITY), "Activity", activityTab.description(), "3", TAB_ACTIVITY, -1));
        entries.add(new TabEntry(icon(TAB_DIAGRAM), "Diagram", diagramTab.description(), "4", TAB_DIAGRAM, -1));
        entries.add(new TabEntry(icon(TAB_ROUTES), "Routes", routesTab.description(), "5", TAB_ROUTES, -1));
        entries.add(new TabEntry(icon(TAB_ENDPOINTS), "Endpoints", endpointsTab.description(), "6", TAB_ENDPOINTS, -1));
        entries.add(new TabEntry(icon(TAB_HTTP), "HTTP", httpTab.description(), "7", TAB_HTTP, -1));
        entries.add(new TabEntry(icon(TAB_HISTORY), "Inspect", historyTab.description(), "8", TAB_HISTORY, -1));
        entries.add(new TabEntry(icon(TAB_ERRORS), "Errors", errorsTab.description(), "9", TAB_ERRORS, -1));
        for (int i = 0; i < moreTabs.size(); i++) {
            MoreTab mt = moreTabs.get(i);
            entries.add(new TabEntry(
                    mt.icon(), mt.name(), mt.tab().description(), String.valueOf(mt.shortcut()),
                    TAB_MORE, i));
        }
        return entries;
    }

    private static String icon(int primaryTabIndex) {
        return TuiIcons.PRIMARY_TAB_ICONS.get(primaryTabIndex);
    }
}
