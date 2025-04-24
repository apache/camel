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
package org.apache.camel.component.platform.http.main;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.impl.BlockingHandlerDecorator;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.platform.http.plugin.JolokiaPlatformHttpPlugin;
import org.apache.camel.component.platform.http.spi.PlatformHttpPluginRegistry;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.http.base.HttpProtocolHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.ReloadStrategy;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.ExceptionHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Camel Management Embedded HTTP server")
public class ManagementHttpServer extends ServiceSupport implements CamelContextAware, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(ManagementHttpServer.class);

    private static final int BODY_MAX_CHARS = 128 * 1024;
    private static final int DEFAULT_POLL_TIMEOUT = 20000;

    private final HeaderFilterStrategy filter = new HttpProtocolHeaderFilterStrategy();

    private VertxPlatformHttpServer server;
    private VertxPlatformHttpRouter router;

    private ProducerTemplate producer;
    private ConsumerTemplate consumer;

    private CamelContext camelContext;
    private PlatformHttpComponent platformHttpComponent;
    private PlatformHttpPluginRegistry pluginRegistry;
    private JolokiaPlatformHttpPlugin jolokiaPlugin;

    private VertxPlatformHttpServerConfiguration configuration = new VertxPlatformHttpServerConfiguration();
    private boolean infoEnabled;
    private boolean devConsoleEnabled;
    private boolean healthCheckEnabled;
    private boolean jolokiaEnabled;
    private boolean metricsEnabled;
    private String healthPath;
    private String jolokiaPath;
    private boolean sendEnabled;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public VertxPlatformHttpServerConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(VertxPlatformHttpServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @ManagedAttribute(description = "Whether file uploads is enabled")
    public boolean isFileUploadEnabled() {
        return configuration.getBodyHandler().isHandleFileUploads();
    }

    public void setFileUploadEnabled(boolean fileUploadEnabled) {
        configuration.getBodyHandler().setHandleFileUploads(fileUploadEnabled);
    }

    @ManagedAttribute(description = "Directory to temporary store file uploads")
    public String getFileUploadDirectory() {
        return configuration.getBodyHandler().getUploadsDirectory();
    }

    public void setFileUploadDirectory(String fileUploadDirectory) {
        configuration.getBodyHandler().setUploadsDirectory(fileUploadDirectory);
    }

    @ManagedAttribute(description = "Whether info is enabled (/observe/info)")
    public boolean isInfoEnabled() {
        return infoEnabled;
    }

    public void setInfoEnabled(boolean infoEnabled) {
        this.infoEnabled = infoEnabled;
    }

    @ManagedAttribute(description = "Whether dev console is enabled (/observe/dev)")
    public boolean isDevConsoleEnabled() {
        return devConsoleEnabled;
    }

    /**
     * Whether developer web console is enabled (q/dev)
     */
    public void setDevConsoleEnabled(boolean devConsoleEnabled) {
        this.devConsoleEnabled = devConsoleEnabled;
    }

    @ManagedAttribute(description = "Whether health check is enabled")
    public boolean isHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    @ManagedAttribute(description = "Whether Jolokia is enabled (observe/jolokia)")
    public boolean isJolokiaEnabled() {
        return jolokiaEnabled;
    }

    /**
     * Whether health-check is enabled (q/health)
     */
    public void setHealthCheckEnabled(boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
    }

    /**
     * Whether jolokia is enabled (q/jolokia)
     */
    public void setJolokiaEnabled(boolean jolokiaEnabledEnabled) {
        this.jolokiaEnabled = jolokiaEnabledEnabled;
    }

    @ManagedAttribute(description = "The context-path for serving health check status")
    public String getHealthPath() {
        return healthPath;
    }

    /**
     * The path endpoint used to expose the health status.
     */
    public void setHealthPath(String healthPath) {
        this.healthPath = healthPath;
    }

    @ManagedAttribute(description = "The context-path for serving Jolokia data")
    public String getJolokiaPath() {
        return jolokiaPath;
    }

    /**
     * The path endpoint used to expose the Jolokia data.
     */
    public void setJolokiaPath(String jolokiaPath) {
        this.jolokiaPath = jolokiaPath;
    }

    @ManagedAttribute(description = "Whether metrics is enabled")
    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    /**
     * Whether metrics is enabled (q/metrics)
     */
    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    @ManagedAttribute(description = "HTTP server port number")
    public int getPort() {
        return configuration.getBindPort();
    }

    public void setPort(int port) {
        configuration.setBindPort(port);
    }

    @ManagedAttribute(description = "HTTP server hostname")
    public String getHost() {
        return configuration.getBindHost();
    }

    public void setHost(String host) {
        configuration.setBindHost(host);
    }

    @ManagedAttribute(description = "HTTP server base path")
    public String getPath() {
        return configuration.getPath();
    }

    public void setPath(String path) {
        configuration.setPath(path);
    }

    @ManagedAttribute(description = "HTTP server maximum body size")
    public Long getMaxBodySize() {
        return configuration.getMaxBodySize();
    }

    public void setMaxBodySize(Long maxBodySize) {
        configuration.setMaxBodySize(maxBodySize);
    }

    public SSLContextParameters getSslContextParameters() {
        return configuration.getSslContextParameters();
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        configuration.setSslContextParameters(sslContextParameters);
    }

    @ManagedAttribute(description = "HTTP server using global SSL context parameters")
    public boolean isUseGlobalSslContextParameters() {
        return configuration.isUseGlobalSslContextParameters();
    }

    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        configuration.setUseGlobalSslContextParameters(useGlobalSslContextParameters);
    }

    @ManagedAttribute(description = "Whether send message is enabled (q/send)")
    public boolean isSendEnabled() {
        return sendEnabled;
    }

    /**
     * Whether to enable sending messages to Camel via HTTP. This makes it possible to use Camel to send messages to
     * Camel endpoint URIs via HTTP.
     */
    public void setSendEnabled(boolean sendEnabled) {
        this.sendEnabled = sendEnabled;
    }

    public VertxPlatformHttpServerConfiguration.Cors getCors() {
        return configuration.getCors();
    }

    public void setCors(VertxPlatformHttpServerConfiguration.Cors corsConfiguration) {
        configuration.setCors(corsConfiguration);
    }

    public VertxPlatformHttpServerConfiguration.BodyHandler getBodyHandler() {
        return configuration.getBodyHandler();
    }

    public void setBodyHandler(VertxPlatformHttpServerConfiguration.BodyHandler bodyHandler) {
        configuration.setBodyHandler(bodyHandler);
    }

    public VertxPlatformHttpRouter getRouter() {
        return router;
    }

    @Override
    protected void doInit() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");

        if (sendEnabled && producer == null) {
            producer = camelContext.createProducerTemplate();
        }
        if (sendEnabled && consumer == null) {
            consumer = camelContext.createConsumerTemplate();
        }

        server = new VertxPlatformHttpServer(configuration);
        // adding server to camel-context which will manage shutdown the server, so we should not do this here
        camelContext.addService(server);

        pluginRegistry = getCamelContext().getCamelContextExtension().getContextPlugin(PlatformHttpPluginRegistry.class);
        if (pluginRegistry == null && pluginsEnabled()) {
            pluginRegistry = resolvePlatformHttpPluginRegistry();
            pluginRegistry.setCamelContext(getCamelContext());
            getCamelContext().getCamelContextExtension().addContextPlugin(PlatformHttpPluginRegistry.class, pluginRegistry);
        }
        ServiceHelper.initService(pluginRegistry);
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");
        ServiceHelper.startService(server, pluginRegistry);
        String routerName = VertxPlatformHttpRouter.getRouterNameFromPort(getPort());
        router = VertxPlatformHttpRouter.lookup(camelContext, routerName);
        platformHttpComponent = camelContext.getComponent("platform-http", PlatformHttpComponent.class);

        setupConsoles();
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(pluginRegistry);
    }

    private boolean pluginsEnabled() {
        return jolokiaEnabled;
    }

    protected void setupConsoles() {
        if (infoEnabled) {
            setupInfo();
        }
        if (devConsoleEnabled) {
            setupDevConsole();
        }
        if (healthCheckEnabled) {
            setupHealthCheckConsole();
        }
        if (jolokiaEnabled) {
            setupJolokia();
        }
        if (sendEnabled) {
            setupSendConsole();
        }
        // metrics will be setup in camel-micrometer-prometheus
    }

    protected void setupInfo() {
        final Route info = router.route("/q/info");
        info.method(HttpMethod.GET);
        info.produces("application/json");

        Handler<RoutingContext> handler = new Handler<RoutingContext>() {

            private String extractState(int status) {
                if (status <= 4) {
                    return "Starting";
                } else if (status == 5) {
                    return "Running";
                } else if (status == 6) {
                    return "Suspending";
                } else if (status == 7) {
                    return "Suspended";
                } else if (status == 8) {
                    return "Terminating";
                } else if (status == 9) {
                    return "Terminated";
                } else {
                    return "Terminated";
                }
            }

            @Override
            public void handle(RoutingContext ctx) {
                ctx.response().putHeader("content-type", "application/json");

                JsonObject root = new JsonObject();

                JsonObject jo = new JsonObject();
                root.put("os", jo);
                jo.put("name", System.getProperty("os.name"));
                jo.put("version", System.getProperty("os.version"));
                jo.put("arch", System.getProperty("os.arch"));

                jo = new JsonObject();
                root.put("java", jo);
                RuntimeMXBean rmb = ManagementFactory.getRuntimeMXBean();
                if (rmb != null) {
                    jo.put("pid", rmb.getPid());
                    jo.put("vendor", rmb.getVmVendor());
                    jo.put("name", rmb.getVmName());
                    jo.put("vmVersion", rmb.getVmVersion());
                    jo.put("version", String.format("%s", System.getProperty("java.version")));
                    jo.put("user", System.getProperty("user.name"));
                    jo.put("dir", System.getProperty("user.dir"));
                    jo.put("home", System.getProperty("user.home"));
                }

                jo = new JsonObject();
                root.put("camel", jo);
                jo.put("name", camelContext.getName());
                jo.put("version", camelContext.getVersion());
                if (camelContext.getCamelContextExtension().getProfile() != null) {
                    jo.put("profile", camelContext.getCamelContextExtension().getProfile());
                }
                if (camelContext.getCamelContextExtension().getDescription() != null) {
                    jo.put("description", camelContext.getCamelContextExtension().getDescription());
                }
                Collection<HealthCheck.Result> results = HealthCheckHelper.invoke(getCamelContext());
                boolean up = results.stream().allMatch(h -> HealthCheck.State.UP.equals(h.getState()));
                jo.put("ready", up ? "1/1" : "0/1");
                jo.put("status", extractState(getCamelContext().getCamelContextExtension().getStatusPhase()));
                int reloaded = 0;
                Set<ReloadStrategy> rs = getCamelContext().hasServices(ReloadStrategy.class);
                for (ReloadStrategy r : rs) {
                    reloaded += r.getReloadCounter();
                }
                jo.put("reload", reloaded);
                jo.put("age", CamelContextHelper.getUptime(camelContext));

                ManagedCamelContext mcc
                        = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
                if (mcc != null) {
                    ManagedCamelContextMBean mb = mcc.getManagedCamelContext();

                    long total = camelContext.getRoutes().stream()
                            .filter(r -> !r.isCreatedByRestDsl() && !r.isCreatedByKamelet()).count();
                    long started = camelContext.getRoutes().stream()
                            .filter(r -> !r.isCreatedByRestDsl() && !r.isCreatedByKamelet())
                            .filter(ServiceHelper::isStarted).count();
                    jo.put("routes", started + "/" + total);
                    String thp = mb.getThroughput();
                    thp = thp.replace(',', '.');
                    if (!thp.isEmpty()) {
                        jo.put("exchangesThroughput", thp + "/s");
                    }
                    jo.put("exchangesTotal", mb.getExchangesTotal());
                    jo.put("exchangesFailed", mb.getExchangesFailed());
                    jo.put("exchangesInflight", mb.getExchangesInflight());
                    if (mb.getExchangesTotal() > 0) {
                        jo.put("lastProcessingTime", mb.getLastProcessingTime());
                        jo.put("deltaProcessingTime", mb.getDeltaProcessingTime());
                    }
                    Date last = mb.getLastExchangeCreatedTimestamp();
                    if (last != null) {
                        jo.put("sinceLastExchangeCreated", TimeUtils.printSince(last.getTime()));
                    }
                    last = mb.getLastExchangeFailureTimestamp();
                    if (last != null) {
                        jo.put("sinceLastExchangeFailed", TimeUtils.printSince(last.getTime()));
                    }
                    last = mb.getLastExchangeCompletedTimestamp();
                    if (last != null) {
                        jo.put("sinceLastExchangeCompleted", TimeUtils.printSince(last.getTime()));
                    }
                }

                ctx.end(root.toJson());
            }
        };

        // use blocking handler as the task can take longer time to complete
        info.handler(new BlockingHandlerDecorator(handler, true));

        platformHttpComponent.addHttpEndpoint("/q/info", "GET", null,
                "application/json", null);
    }

    protected void setupHealthCheckConsole() {
        final Route health = router.route(this.healthPath);
        health.method(HttpMethod.GET);
        health.produces("application/json");
        final Route live = router.route(this.healthPath + "/live");
        live.method(HttpMethod.GET);
        live.produces("application/json");
        final Route ready = router.route(this.healthPath + "/ready");
        ready.method(HttpMethod.GET);
        ready.produces("application/json");

        Handler<RoutingContext> handler = new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext ctx) {
                ctx.response().putHeader("content-type", "application/json");

                HealthCheckRegistry registry = HealthCheckRegistry.get(camelContext);
                String level = ctx.request().getParam("exposureLevel");
                if (level == null) {
                    level = registry.getExposureLevel();
                }
                String includeStackTrace = ctx.request().getParam("stackTrace");
                String includeData = ctx.request().getParam("data");

                boolean all = ctx.currentRoute() == health;
                boolean liv = ctx.currentRoute() == live;
                boolean rdy = ctx.currentRoute() == ready;

                Collection<HealthCheck.Result> res;
                if (all) {
                    res = HealthCheckHelper.invoke(camelContext, level);
                } else if (liv) {
                    res = HealthCheckHelper.invokeLiveness(camelContext, level);
                } else {
                    res = HealthCheckHelper.invokeReadiness(camelContext, level);
                }

                StringBuilder sb = new StringBuilder();
                sb.append("{\n");

                // are we UP
                boolean up = HealthCheckHelper.isResultsUp(res, rdy);

                if ("oneline".equals(level)) {
                    // only brief status
                    healthCheckStatus(sb, up);
                } else if ("full".equals(level)) {
                    // include all details
                    List<HealthCheck.Result> list = new ArrayList<>(res);
                    healthCheckDetails(sb, list, up, level, includeStackTrace, includeData);
                } else {
                    // include only DOWN details
                    List<HealthCheck.Result> downs = res.stream().filter(r -> r.getState().equals(HealthCheck.State.DOWN))
                            .collect(Collectors.toList());
                    healthCheckDetails(sb, downs, up, level, includeStackTrace, includeData);
                }
                sb.append("}\n");

                if (!up) {
                    // we need to fail with a http status so lets use 500
                    ctx.response().setStatusCode(503);
                }
                ctx.end(sb.toString());
            }
        };
        // use blocking handler as the task can take longer time to complete
        health.handler(new BlockingHandlerDecorator(handler, true));
        live.handler(new BlockingHandlerDecorator(handler, true));
        ready.handler(new BlockingHandlerDecorator(handler, true));

        platformHttpComponent.addHttpEndpoint(this.healthPath, "GET", null,
                "application/json", null);
    }

    protected void setupJolokia() {
        // load plugin
        jolokiaPlugin = pluginRegistry.resolvePluginById(JolokiaPlatformHttpPlugin.NAME, JolokiaPlatformHttpPlugin.class)
                .orElseThrow(() -> new RuntimeException(
                        "JolokiaPlatformHttpPlugin not found. Please add camel-platform-http-jolokia dependency."));

        Route jolokia = router.route(jolokiaPath + "/*");
        jolokia.method(HttpMethod.GET);
        jolokia.method(HttpMethod.POST);

        Handler<RoutingContext> handler = (Handler<RoutingContext>) jolokiaPlugin.getHandler();
        jolokia.handler(new BlockingHandlerDecorator(handler, true));

        platformHttpComponent.addHttpEndpoint(jolokiaPath, "GET,POST", null,
                "text/plain,application/json", null);
    }

    protected void setupSendConsole() {
        final Route send = router.route("/q/send/")
                .produces("application/json")
                .method(HttpMethod.GET).method(HttpMethod.POST)
                // need body handler to have access to the body
                .handler(BodyHandler.create(false));

        Handler<RoutingContext> handler = new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext ctx) {
                try {
                    doSend(ctx);
                } catch (Exception e) {
                    LOG.warn("Error sending Camel message due to: " + e.getMessage(), e);
                    if (!ctx.response().ended()) {
                        ctx.response().setStatusCode(500);
                        ctx.end();
                    }
                }
            }
        };
        // use blocking handler as the task can take longer time to complete
        send.handler(new BlockingHandlerDecorator(handler, true));

        platformHttpComponent.addHttpEndpoint("/q/send", "GET,POST",
                null, "application/json", null);
    }

    protected PlatformHttpPluginRegistry resolvePlatformHttpPluginRegistry() {
        Optional<PlatformHttpPluginRegistry> result = ResolverHelper.resolveService(
                getCamelContext(),
                PlatformHttpPluginRegistry.FACTORY,
                PlatformHttpPluginRegistry.class);
        return result.orElseThrow(() -> new IllegalArgumentException(
                "Cannot create PlatformHttpPluginRegistry. Make sure camel-platform-http JAR is on classpath."));
    }

    private static void healthCheckStatus(StringBuilder sb, boolean up) {
        if (up) {
            sb.append("    \"status\": \"UP\"\n");
        } else {
            sb.append("    \"status\": \"DOWN\"\n");
        }
    }

    private static void healthCheckDetails(
            StringBuilder sb, List<HealthCheck.Result> checks, boolean up, String level, String includeStackTrace,
            String includeData) {
        healthCheckStatus(sb, up);

        if (!checks.isEmpty()) {
            sb.append(",\n");
            sb.append("    \"checks\": [\n");
            for (int i = 0; i < checks.size(); i++) {
                HealthCheck.Result d = checks.get(i);
                sb.append("        {\n");
                reportHealthCheck(sb, d, level, includeStackTrace, includeData);
                if (i < checks.size() - 1) {
                    sb.append("        },\n");
                } else {
                    sb.append("        }\n");
                }
            }
            sb.append("    ]\n");
        }
    }

    private static void reportHealthCheck(
            StringBuilder sb, HealthCheck.Result d, String level, String includeStackTrace, String includeData) {
        sb.append("            \"name\": \"").append(d.getCheck().getId()).append("\",\n");
        sb.append("            \"status\": \"").append(d.getState()).append("\"");
        if (("full".equals(level) || "true".equals(includeStackTrace)) && d.getError().isPresent()) {
            // include error message in full exposure
            sb.append(",\n");
            String msg = allCausedByErrorMessages(d.getError().get());
            sb.append("            \"error-message\": \"").append(msg)
                    .append("\"");
            if ("true".equals(includeStackTrace)) {
                sb.append(",\n");
                sb.append("            \"error-stacktrace\": \"").append(errorStackTrace(d.getError().get()))
                        .append("\"");
            }
        }
        if (d.getMessage().isPresent()) {
            sb.append(",\n");
            sb.append("            \"message\": \"").append(d.getMessage().get()).append("\"");
        }
        // only include data if was enabled
        if (("true".equals(includeData)) && d.getDetails() != null && !d.getDetails().isEmpty()) {
            sb.append(",\n");
            // lets use sorted keys
            Iterator<String> it = new TreeSet<>(d.getDetails().keySet()).iterator();
            sb.append("            \"data\": {\n");
            while (it.hasNext()) {
                String k = it.next();
                Object v = d.getDetails().get(k);
                if (v == null) {
                    v = ""; // in case of null value
                }
                boolean last = !it.hasNext();
                sb.append("                 \"").append(k).append("\": \"").append(v).append("\"");
                if (!last) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("            }\n");
        }
    }

    private static String allCausedByErrorMessages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getMessage());

        while (e.getCause() != null) {
            e = e.getCause();
            if (e.getMessage() != null) {
                sb.append("; Caused by: ");
                sb.append(ObjectHelper.classCanonicalName(e));
                sb.append(": ");
                sb.append(e.getMessage());
            }
        }

        return sb.toString();
    }

    private static String errorStackTrace(Throwable e) {
        String trace = ExceptionHelper.stackTraceToString(e);
        // because the stacktrace is printed in json we need to make it safe
        trace = trace.replace('"', '\'');
        trace = trace.replace('\t', ' ');
        trace = trace.replace(System.lineSeparator(), " ");
        return trace;
    }

    protected void setupDevConsole() {
        Route dev = router.route("/q/dev");
        dev.method(HttpMethod.GET);
        dev.produces("text/plain");
        dev.produces("application/json");
        Route devSub = router.route("/q/dev/*");
        devSub.method(HttpMethod.GET);
        devSub.produces("text/plain");
        devSub.produces("application/json");

        Handler<RoutingContext> handler = new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext ctx) {
                String acp = ctx.request().getHeader("Accept");
                int pos1 = acp != null ? acp.indexOf("html") : Integer.MAX_VALUE;
                if (pos1 == -1) {
                    pos1 = Integer.MAX_VALUE;
                }
                int pos2 = acp != null ? acp.indexOf("json") : Integer.MAX_VALUE;
                if (pos2 == -1) {
                    pos2 = Integer.MAX_VALUE;
                }
                final boolean html = pos1 < pos2;
                final boolean json = pos2 < pos1;
                final DevConsole.MediaType mediaType = json ? DevConsole.MediaType.JSON : DevConsole.MediaType.TEXT;

                ctx.response().putHeader("content-type", "text/plain");

                if (!camelContext.isDevConsole()) {
                    ctx.end("Developer Console is not enabled on CamelContext. Set camel.context.dev-console=true in application.properties");
                }
                DevConsoleRegistry dcr = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
                if (dcr == null) {
                    ctx.end("Developer Console is not included. Add camel-console to classpath.");
                    return;
                } else if (!dcr.isEnabled()) {
                    ctx.end("Developer Console is not enabled");
                    return;
                }

                String path = StringHelper.after(ctx.request().path(), "/q/dev/");
                String s = path;
                if (s != null && s.contains("/")) {
                    s = StringHelper.before(s, "/");
                }
                String id = s;

                // index/home should list each console
                if (id == null || id.isEmpty() || id.equals("index")) {
                    StringBuilder sb = new StringBuilder();
                    JsonObject root = new JsonObject();

                    dcr.stream().forEach(c -> {
                        if (json) {
                            JsonObject jo = new JsonObject();
                            jo.put("id", c.getId());
                            jo.put("displayName", c.getDisplayName());
                            jo.put("description", c.getDescription());
                            root.put(c.getId(), jo);
                        } else {
                            String link = c.getId();
                            String eol = "\n";
                            if (html) {
                                link = "<a href=\"dev/" + link + "\">" + c.getId() + "</a>";
                                eol = "<br/>\n";
                            }
                            sb.append(link).append(": ").append(c.getDescription()).append(eol);
                            // special for top in processor mode
                            if ("top".equals(c.getId())) {
                                link = link.replace("top", "top/*");
                                sb.append(link).append(": ").append("Display the top processors").append(eol);
                            }
                        }
                    });
                    if (!sb.isEmpty()) {
                        String out = sb.toString();
                        if (html) {
                            ctx.response().putHeader("content-type", "text/html");
                        }
                        ctx.end(out);
                    } else if (!root.isEmpty()) {
                        ctx.response().putHeader("content-type", "application/json");
                        String out = root.toJson();
                        ctx.end(out);
                    } else {
                        ctx.end();
                    }
                } else {
                    Map<String, Object> params = new HashMap<>();
                    ctx.queryParams().forEach(params::put);
                    params.put(Exchange.HTTP_PATH, path);
                    StringBuilder sb = new StringBuilder();
                    JsonObject root = new JsonObject();

                    // sort according to index by given id
                    dcr.stream().sorted((o1, o2) -> {
                        int p1 = id.indexOf(o1.getId());
                        int p2 = id.indexOf(o2.getId());
                        return Integer.compare(p1, p2);
                    }).forEach(c -> {
                        boolean include = "all".equals(id) || c.getId().equalsIgnoreCase(id);
                        if (include && c.supportMediaType(mediaType)) {
                            Object out = c.call(mediaType, params);
                            if (out != null && mediaType == DevConsole.MediaType.TEXT) {
                                sb.append(c.getDisplayName()).append(":");
                                sb.append("\n\n");
                                sb.append(out);
                                sb.append("\n\n");
                            } else if (out != null && mediaType == DevConsole.MediaType.JSON) {
                                root.put(c.getId(), out);
                            }
                        }
                    });
                    if (!sb.isEmpty()) {
                        String out = sb.toString();
                        ctx.end(out);
                    } else if (!root.isEmpty()) {
                        ctx.response().putHeader("content-type", "application/json");
                        String out = root.toJson();
                        ctx.end(out);
                    } else {
                        ctx.end("Developer Console not found: " + id);
                    }
                }
            }
        };
        // use blocking handler as the task can take longer time to complete
        dev.handler(new BlockingHandlerDecorator(handler, true));
        devSub.handler(new BlockingHandlerDecorator(handler, true));

        platformHttpComponent.addHttpEndpoint("/q/dev", "GET", null,
                "text/plain,application/json", null);
    }

    protected void doSend(RoutingContext ctx) {
        StopWatch watch = new StopWatch();
        long timestamp = System.currentTimeMillis();

        String endpoint = ctx.request().getHeader("endpoint");
        String exchangePattern = ctx.request().getHeader("exchangePattern");
        String resultType = ctx.request().getHeader("resultType");
        String poll = ctx.request().getHeader("poll");
        String pollTimeout = ctx.request().getHeader("pollTimeout");
        final Map<String, Object> headers = new LinkedHashMap<>();
        for (var entry : ctx.request().headers()) {
            String k = entry.getKey();
            boolean exclude
                    = "endpoint".equals(k) || "exchangePattern".equals(k) || "poll".equals(k)
                            || "pollTimeout".equals(k) || "resultType".equals(k) || "Accept".equals(k)
                            || filter.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), null);
            if (!exclude) {
                headers.put(entry.getKey(), entry.getValue());
            }
        }
        final String body = ctx.body().asString();

        Exchange out = null;
        Endpoint target = null;
        if (endpoint == null) {
            List<org.apache.camel.Route> routes = camelContext.getRoutes();
            if (!routes.isEmpty()) {
                // grab endpoint from 1st route
                target = routes.get(0).getEndpoint();
            }
        } else {
            // is the endpoint a pattern or route id
            boolean scheme = endpoint.contains(":");
            boolean pattern = endpoint.endsWith("*");
            if (!scheme || pattern) {
                if (!scheme) {
                    endpoint = endpoint + "*";
                }
                String quotedEndpoint = Pattern.quote(endpoint);
                for (org.apache.camel.Route route : camelContext.getRoutes()) {
                    Endpoint e = route.getEndpoint();
                    if (EndpointHelper.matchEndpoint(camelContext, e.getEndpointUri(), quotedEndpoint)) {
                        target = e;
                        break;
                    }
                }
                if (target == null) {
                    // okay it may refer to a route id
                    for (org.apache.camel.Route route : camelContext.getRoutes()) {
                        String id = route.getRouteId();
                        Endpoint e = route.getEndpoint();
                        if (EndpointHelper.matchEndpoint(camelContext, id, quotedEndpoint)) {
                            target = e;
                            break;
                        }
                    }
                }
            } else {
                target = camelContext.getEndpoint(endpoint);
            }
        }

        JsonObject jo = new JsonObject();
        if (target != null) {
            Class<?> clazz = null;
            try {
                if (resultType != null) {
                    clazz = camelContext.getClassResolver().resolveMandatoryClass(resultType);
                    // we want the result as a specific type then make sure to use InOut
                    if (exchangePattern == null) {
                        exchangePattern = "InOut";
                    }
                }
                if (exchangePattern == null) {
                    exchangePattern = "InOnly"; // use in-only by default
                }
                final ExchangePattern mep = ExchangePattern.valueOf(exchangePattern);
                long timeout = pollTimeout != null ? Long.parseLong(pollTimeout) : DEFAULT_POLL_TIMEOUT;
                if ("true".equals(poll)) {
                    exchangePattern = "InOut"; // we want to receive the data so enable out mode
                    out = consumer.receive(target, timeout);
                } else {
                    out = producer.send(target, exchange -> {
                        exchange.setPattern(mep);
                        exchange.getMessage().setBody(body);
                        if (!headers.isEmpty()) {
                            exchange.getMessage().setHeaders(headers);
                        }
                    });
                }
                if (clazz != null && out != null) {
                    Object b = out.getMessage().getBody(clazz);
                    out.getMessage().setBody(b);
                }
            } catch (Exception e) {
                jo.put("endpoint", target.getEndpointUri());
                jo.put("exchangePattern", exchangePattern);
                jo.put("timestamp", timestamp);
                jo.put("elapsed", watch.taken());
                jo.put("status", "failed");
                jo.put("exception",
                        MessageHelper.dumpExceptionAsJSonObject(e).getMap("exception"));
            }
            if (out != null && out.getException() != null) {
                jo.put("endpoint", target.getEndpointUri());
                jo.put("exchangeId", out.getExchangeId());
                jo.put("exchangePattern", exchangePattern);
                jo.put("timestamp", timestamp);
                jo.put("elapsed", watch.taken());
                jo.put("status", "failed");
                // avoid double wrap
                jo.put("exception",
                        MessageHelper.dumpExceptionAsJSonObject(out.getException()).getMap("exception"));
            } else if (out != null && "InOut".equals(exchangePattern)) {
                jo.put("endpoint", target.getEndpointUri());
                jo.put("exchangeId", out.getExchangeId());
                jo.put("exchangePattern", exchangePattern);
                jo.put("timestamp", timestamp);
                jo.put("elapsed", watch.taken());
                jo.put("status", "success");
                // dump response and remove unwanted data
                JsonObject msg = MessageHelper.dumpAsJSonObject(out.getMessage(), false, false, true, true, true, true,
                        BODY_MAX_CHARS).getMap("message");
                msg.remove("exchangeId");
                msg.remove("exchangePattern");
                msg.remove("exchangeType");
                msg.remove("messageType");
                jo.put("message", msg);
            } else if (out != null) {
                jo.put("endpoint", target.getEndpointUri());
                jo.put("exchangeId", out.getExchangeId());
                jo.put("exchangePattern", exchangePattern);
                jo.put("timestamp", timestamp);
                jo.put("elapsed", watch.taken());
                jo.put("status", "success");
            } else {
                // timeout as there is no data
                jo.put("endpoint", target.getEndpointUri());
                jo.put("timestamp", timestamp);
                jo.put("elapsed", watch.taken());
                jo.put("status", "timeout");
            }
        } else {
            // there is no valid endpoint
            ctx.response().setStatusCode(400);
            jo.put("endpoint", endpoint);
            jo.put("exchangeId", "");
            jo.put("exchangePattern", exchangePattern);
            jo.put("timestamp", timestamp);
            jo.put("elapsed", watch.taken());
            jo.put("status", "failed");
            // avoid double wrap
            jo.put("exception",
                    MessageHelper.dumpExceptionAsJSonObject(new NoSuchEndpointException(endpoint))
                            .getMap("exception"));
        }
        ctx.end(jo.toJson());
    }

}
