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
import java.util.function.Predicate;

import dev.tamboui.widgets.tabs.TabsState;

/**
 * Owns all tab instances, tab index constants, and tab navigation logic. Extracted from {@link CamelMonitor} to reduce
 * class size.
 */
class TabRegistry {

    // Tab indices
    static final int TAB_OVERVIEW = 0;
    static final int TAB_SOURCE = 1;
    static final int TAB_LOG = 2;
    static final int TAB_ACTIVITY = 3;
    static final int TAB_DIAGRAM = 4;
    static final int TAB_ROUTES = 5;
    static final int TAB_ENDPOINTS = 6;
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
    private ProducersTab producersTab;
    private EventTab eventTab;
    private RouteControllerTab routeControllerTab;
    private EndpointsTab endpointsTab;
    private NetworkTab networkTab;
    private HttpTab httpTab;
    private SourceTab sourceTab;
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
    private CatalogTab catalogTab;
    private ClasspathTab classpathTab;
    private MavenDependenciesTab mavenDependenciesTab;
    private CveAuditTab cveAuditTab;
    private InflightTab inflightTab;
    private MemoryTab memoryTab;
    private HeapHistogramTab heapHistogramTab;
    private MemoryLeakTab memoryLeakTab;
    private JfrTab jfrTab;
    private ThreadsTab threadsTab;
    private SpansTab spansTab;
    private ProcessTab processTab;
    private OverviewTab overviewTab;
    private KafkaTab kafkaTab;
    private DataSourceTab dataSourceTab;
    private SqlQueryTab sqlQueryTab;
    private InternalTasksTab internalTasksTab;
    private SqlTraceTab sqlTraceTab;
    private TypeConvertersTab typeConvertersTab;
    private TransformersTab transformersTab;
    private SecretsTab secretsTab;

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
        producersTab = new ProducersTab(ctx);
        eventTab = new EventTab(ctx);
        routeControllerTab = new RouteControllerTab(ctx);
        kafkaTab = new KafkaTab(ctx);
        dataSourceTab = new DataSourceTab(ctx);
        heapHistogramTab = new HeapHistogramTab(ctx);
        memoryLeakTab = new MemoryLeakTab(ctx);
        jfrTab = new JfrTab(ctx);
        sqlQueryTab = new SqlQueryTab(ctx);
        internalTasksTab = new InternalTasksTab(ctx);
        sqlTraceTab = new SqlTraceTab(ctx);
        typeConvertersTab = new TypeConvertersTab(ctx);
        transformersTab = new TransformersTab(ctx);
        secretsTab = new SecretsTab(ctx);
        endpointsTab = new EndpointsTab(ctx, dataService.metrics());
        networkTab = new NetworkTab(ctx, dataService.metrics());
        httpTab = new HttpTab(ctx);
        sourceTab = new SourceTab(ctx);
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
        catalogTab = new CatalogTab(ctx);
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

        // Single source of truth for the More submenu: icon, programmatic name, mnemonic label, tab instance, and group.
        // Tabs are ordered by group (Routing, Observability, Data, JVM, Project), alphabetically within each group.
        // The group field drives divider rendering in the More popup.
        moreTabs = List.of(
                // Routing
                new MoreTab(
                        TuiIcons.TAB_BROWSE, "Browse Endpoints", "&Browse Endpoints", browseTab, "Routing",
                        List.of(), info -> info.hasBrowseableEndpoints),
                new MoreTab(TuiIcons.TAB_CONSUMERS, "Consumers", "&Consumers", consumersTab, "Routing"),
                new MoreTab(
                        TuiIcons.TAB_HTTP, "HTTP", "&HTTP", httpTab, "Routing",
                        List.of("platform-http"), info -> !info.httpEndpoints.isEmpty()),
                new MoreTab(TuiIcons.TAB_INFLIGHT, "Inflight", "&Inflight", inflightTab, "Routing"),
                new MoreTab(
                        TuiIcons.TAB_PRODUCERS, "Producers", "&Producers", producersTab, "Routing",
                        List.of(), info -> !info.producers.isEmpty()),
                new MoreTab(
                        TuiIcons.TAB_ROUTE_CONTROLLER, "Route Controller", "&Route Controller", routeControllerTab,
                        "Routing",
                        List.of(), info -> "SupervisingRouteController".equals(info.routeControllerType)),
                // Observability
                new MoreTab(
                        TuiIcons.TAB_CIRCUIT_BREAKER, "Circuit Breaker", "&Circuit Breaker", circuitBreakerTab,
                        "Observability",
                        List.of("resilience4j", "fault-tolerance")),
                new MoreTab(TuiIcons.TAB_HEALTH, "Health", "&Health", healthTab, "Observability"),
                new MoreTab(
                        TuiIcons.TAB_METRICS, "Metrics", "&Metrics", metricsTab, "Observability",
                        List.of("micrometer")),
                new MoreTab(
                        TuiIcons.TAB_NETWORK, "Network Services", "&Network Services", networkTab, "Observability",
                        List.of(), info -> !info.services.isEmpty()),
                new MoreTab(TuiIcons.TAB_EVENTS, "Events", "&Exchange Events", eventTab, "Observability"),
                new MoreTab(
                        TuiIcons.TAB_RECOVERY_TASKS, "Recovery Tasks", "&Recovery Tasks", internalTasksTab,
                        "Observability",
                        List.of(), info -> !info.internalTasks.isEmpty()),
                new MoreTab(
                        TuiIcons.TAB_SPANS, "Spans", "&OTel Spans", spansTab, "Observability",
                        List.of("opentelemetry")),
                // Data
                new MoreTab(
                        TuiIcons.TAB_DATASOURCE, "JDBC DataSource", "&JDBC DataSource", dataSourceTab, "Data",
                        List.of(), info -> !info.dataSources.isEmpty()),
                new MoreTab(
                        TuiIcons.TAB_KAFKA, "Kafka", "&Kafka", kafkaTab, "Data",
                        List.of("kafka")),
                new MoreTab(
                        TuiIcons.TAB_SECRETS, "Secrets", "&Secrets", secretsTab, "Data",
                        List.of("aws-secrets", "azure-secrets", "gcp-secrets", "hashicorp-secrets",
                                "ibm-secrets", "kubernetes-secrets", "kubernetes-configmaps"),
                        info -> !info.vaultSecrets.isEmpty()),
                new MoreTab(
                        TuiIcons.TAB_SQL_QUERY, "SQL Query", "S&QL Query", sqlQueryTab, "Data",
                        List.of(), info -> !info.dataSources.isEmpty()),
                new MoreTab(
                        TuiIcons.TAB_SQL_TRACE, "SQL Trace", "S&QL Trace", sqlTraceTab, "Data",
                        List.of(), info -> !info.dataSources.isEmpty()),
                // JVM
                new MoreTab(TuiIcons.TAB_CLASSPATH, "Classpath", "&Classpath", classpathTab, "JVM"),
                new MoreTab(TuiIcons.TAB_HEAP, "Heap Memory Histogram", "Heap &Memory Histogram", heapHistogramTab, "JVM"),
                new MoreTab(
                        TuiIcons.TAB_JFR, "Java Flight Recorder (JFR)", "Java Fli&ght Recorder (JFR)", jfrTab, "JVM",
                        List.of("jfr")),
                new MoreTab(TuiIcons.TAB_MEMORY, "Memory Usage", "&Memory Usage", memoryTab, "JVM"),
                new MoreTab(TuiIcons.TAB_MEMORY_LEAK, "Memory Leak", "&Memory Leak", memoryLeakTab, "JVM"),
                new MoreTab(TuiIcons.TAB_PROCESS, "Process", "&Process", processTab, "JVM"),
                new MoreTab(TuiIcons.TAB_STARTUP, "Startup", "&Startup", startupTab, "JVM"),
                new MoreTab(TuiIcons.TAB_THREADS, "Threads", "&Threads", threadsTab, "JVM"),
                // Project
                new MoreTab(TuiIcons.TAB_BEANS, "Beans", "&Beans", beansTab, "Project"),
                new MoreTab(TuiIcons.TAB_CATALOG, "Catalog", "&Catalog", catalogTab, "Project"),
                new MoreTab(TuiIcons.TAB_CONFIGURATION, "Configuration", "&Configuration", configurationTab, "Project"),
                new MoreTab(TuiIcons.TAB_CVE_AUDIT, "CVE Audit", "C&VE Audit", cveAuditTab, "Project"),
                new MoreTab(
                        TuiIcons.TAB_MAVEN_DEPENDENCIES, "Maven Dependencies", "&Maven Dependencies",
                        mavenDependenciesTab,
                        "Project"),
                new MoreTab(
                        TuiIcons.TAB_TYPE_CONVERTERS, "Type Converters", "T&ype Converters",
                        typeConvertersTab, "Project"),
                new MoreTab(
                        TuiIcons.TAB_TRANSFORMERS, "Transformers", "Trans&formers",
                        transformersTab, "Project",
                        List.of(), info -> info.transformerCount > 0));
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
            case TAB_SOURCE -> sourceTab;
            case TAB_HISTORY -> historyTab;
            case TAB_ERRORS -> errorsTab;
            case TAB_MORE -> activeMoreTab;
            default -> null;
        };
    }

    MonitorTab findTabByName(String name) {
        for (TabEntry entry : allTabEntries()) {
            if (entry.name().equalsIgnoreCase(name)) {
                return entry.tab();
            }
        }
        return null;
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
        sourceTab.onIntegrationChanged();
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

    SourceTab sourceTab() {
        return sourceTab;
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

    CatalogTab catalogTab() {
        return catalogTab;
    }

    CveAuditTab cveAuditTab() {
        return cveAuditTab;
    }

    // ---- Tab entries for Go-to and MCP ----

    record TabEntry(String icon, String name, String description, String shortcut, int tabIndex, int moreIndex,
            MonitorTab tab) {

        TabEntry(String icon, String name, String description, String shortcut, int tabIndex, int moreIndex) {
            this(icon, name, description, shortcut, tabIndex, moreIndex, null);
        }
    }

    /**
     * A "More" submenu tab. Bundles its icon, programmatic {@code name} (used for tab lookup and the Go to… popup),
     * popup {@code label} carrying a {@value TuiIcons#MNEMONIC_MARKER} shortcut marker, and the tab instance. The
     * shortcut letter and its highlight offset are derived from {@code label} via
     * {@link TuiIcons#mnemonicIndex(String)}, so there is no separate index or shortcut list to keep aligned.
     */
    /**
     * @param requiredConsoles dev console IDs that must be present (any-of) for this tab to be active; empty = always
     *                         active
     * @param activeWhen       runtime predicate on IntegrationInfo; null = always active (after console check)
     */
    record MoreTab(String icon, String name, String label, MonitorTab tab, String group,
            List<String> requiredConsoles, Predicate<IntegrationInfo> activeWhen) {

        MoreTab(String icon, String name, String label, MonitorTab tab, String group,
                List<String> requiredConsoles) {
            this(icon, name, label, tab, group, requiredConsoles, null);
        }

        MoreTab(String icon, String name, String label, MonitorTab tab, String group) {
            this(icon, name, label, tab, group, List.of(), null);
        }

        MoreTab(String icon, String name, String label, MonitorTab tab) {
            this(icon, name, label, tab, null, List.of(), null);
        }

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

    static boolean isMoreTabActive(MoreTab mt, IntegrationInfo info) {
        if (mt.requiredConsoles().isEmpty() && mt.activeWhen() == null) {
            return true;
        }
        if (info == null) {
            return true;
        }
        if (!mt.requiredConsoles().isEmpty()) {
            if (info.devConsoles.isEmpty()) {
                // old Camel without devConsoles reporting — show all
            } else if (mt.requiredConsoles().stream().noneMatch(info.devConsoles::contains)) {
                return false;
            }
        }
        if (mt.activeWhen() != null && !mt.activeWhen().test(info)) {
            return false;
        }
        return true;
    }

    List<MoreTab> activeMoreTabs(IntegrationInfo info) {
        return moreTabs.stream().filter(mt -> isMoreTabActive(mt, info)).toList();
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
        return allTabEntries(null);
    }

    List<TabEntry> allTabEntries(IntegrationInfo info) {
        List<TabEntry> entries = new ArrayList<>();
        entries.add(
                new TabEntry(icon(TAB_OVERVIEW), "Overview", overviewTab.description(), "1", TAB_OVERVIEW, -1, overviewTab));
        entries.add(new TabEntry(icon(TAB_SOURCE), "Source", sourceTab.description(), "2", TAB_SOURCE, -1, sourceTab));
        entries.add(new TabEntry(icon(TAB_LOG), "Log", logTab.description(), "3", TAB_LOG, -1, logTab));
        entries.add(
                new TabEntry(icon(TAB_ACTIVITY), "Activity", activityTab.description(), "4", TAB_ACTIVITY, -1, activityTab));
        entries.add(new TabEntry(icon(TAB_DIAGRAM), "Diagram", diagramTab.description(), "5", TAB_DIAGRAM, -1, diagramTab));
        entries.add(new TabEntry(icon(TAB_ROUTES), "Routes", routesTab.description(), "6", TAB_ROUTES, -1, routesTab));
        entries.add(new TabEntry(
                icon(TAB_ENDPOINTS), "Endpoints", endpointsTab.description(), "7", TAB_ENDPOINTS, -1, endpointsTab));
        entries.add(new TabEntry(icon(TAB_HISTORY), "Inspect", historyTab.description(), "8", TAB_HISTORY, -1, historyTab));
        entries.add(new TabEntry(icon(TAB_ERRORS), "Errors", errorsTab.description(), "9", TAB_ERRORS, -1, errorsTab));
        for (int i = 0; i < moreTabs.size(); i++) {
            MoreTab mt = moreTabs.get(i);
            if (!isMoreTabActive(mt, info)) {
                continue;
            }
            entries.add(new TabEntry(
                    mt.icon(), mt.name(), mt.tab().description(), String.valueOf(mt.shortcut()),
                    TAB_MORE, i, mt.tab()));
        }
        return entries;
    }

    private static String icon(int primaryTabIndex) {
        return TuiIcons.PRIMARY_TAB_ICONS.get(primaryTabIndex);
    }
}
