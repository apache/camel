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

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.impl.BlockingHandlerDecorator;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.StartupListener;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.platform.http.HttpEndpointModel;
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
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Camel Main Embedded HTTP server")
public class MainHttpServer extends ServiceSupport implements CamelContextAware, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(MainHttpServer.class);

    private VertxPlatformHttpServer server;
    private VertxPlatformHttpRouter router;

    private CamelContext camelContext;
    private PlatformHttpComponent platformHttpComponent;
    private PlatformHttpPluginRegistry pluginRegistry;
    private JolokiaPlatformHttpPlugin jolokiaPlugin;

    private VertxPlatformHttpServerConfiguration configuration = new VertxPlatformHttpServerConfiguration();
    private boolean devConsoleEnabled;
    private boolean healthCheckEnabled;
    private boolean jolokiaEnabled;
    private boolean metricsEnabled;
    private boolean uploadEnabled;
    private String uploadSourceDir;

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

    @ManagedAttribute(description = "Whether dev console is enabled (/q/dev)")
    public boolean isDevConsoleEnabled() {
        return devConsoleEnabled;
    }

    /**
     * Whether developer web console is enabled (q/dev)
     */
    public void setDevConsoleEnabled(boolean devConsoleEnabled) {
        this.devConsoleEnabled = devConsoleEnabled;
    }

    @ManagedAttribute(description = "Whether health check is enabled (q/health)")
    public boolean isHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    @ManagedAttribute(description = "Whether Jolokia is enabled (q/jolokia)")
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

    @ManagedAttribute(description = "Whether metrics is enabled (q/metric)")
    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    /**
     * Whether metrics is enabled (q/metrics)
     */
    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    @ManagedAttribute(description = "Whether file upload is enabled (only for development) (q/upload)")
    public boolean isUploadEnabled() {
        return uploadEnabled;
    }

    /**
     * Whether file upload is enabled (only for development) (q/upload)
     */
    public void setUploadEnabled(boolean uploadEnabled) {
        this.uploadEnabled = uploadEnabled;
    }

    @ManagedAttribute(description = "Directory for upload.")
    public String getUploadSourceDir() {
        return uploadSourceDir;
    }

    /**
     * Directory for upload.
     */
    public void setUploadSourceDir(String uploadSourceDir) {
        this.uploadSourceDir = uploadSourceDir;
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

        server = new VertxPlatformHttpServer(configuration);
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
        router = VertxPlatformHttpRouter.lookup(camelContext);
        platformHttpComponent = camelContext.getComponent("platform-http", PlatformHttpComponent.class);

        setupConsoles();
        setupStartupSummary();
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(pluginRegistry);
    }

    private boolean pluginsEnabled() {
        return jolokiaEnabled;
    }

    protected void setupConsoles() {
        if (devConsoleEnabled) {
            setupDevConsole();
        }
        if (healthCheckEnabled) {
            setupHealthCheckConsole();
        }
        if (jolokiaEnabled) {
            setupJolokia();
        }
        if (uploadEnabled) {
            if (uploadSourceDir == null) {
                throw new IllegalArgumentException("UploadSourceDir must be configured when uploadEnabled=true");
            }
            setupUploadConsole(uploadSourceDir);
        }
        // metrics will be setup in camel-micrometer-prometheus
    }

    protected void setupStartupSummary() throws Exception {
        camelContext.addStartupListener(new StartupListener() {

            private volatile Set<HttpEndpointModel> last;

            private void logSummary() {
                Set<HttpEndpointModel> endpoints = platformHttpComponent.getHttpEndpoints();
                if (endpoints.isEmpty()) {
                    return;
                }

                // log only if changed
                if (last == null || last.size() != endpoints.size() || !last.containsAll(endpoints)) {
                    LOG.info("HTTP endpoints summary");
                    for (HttpEndpointModel u : endpoints) {
                        String line = "http://0.0.0.0:" + (server != null ? server.getPort() : getPort()) + u.getUri();
                        if (u.getVerbs() != null) {
                            line += "    (" + u.getVerbs() + ")";
                        }
                        if (u.getConsumes() != null || u.getProduces() != null) {
                            line += "    (";
                            if (u.getConsumes() != null) {
                                line += "accept:" + u.getConsumes();
                                if (u.getProduces() != null) {
                                    line += " ";
                                }
                            }
                            if (u.getProduces() != null) {
                                line += "produce:" + u.getProduces();
                            }
                            line += ")";
                        }
                        LOG.info("    {}", line);
                    }
                }

                // use a defensive copy of last known endpoints
                last = new HashSet<>(endpoints);
            }

            @Override
            public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) {
                if (alreadyStarted) {
                    logSummary();
                }
                camelContext.getManagementStrategy().addEventNotifier(new SimpleEventNotifierSupport() {

                    @Override
                    public boolean isEnabled(CamelEvent event) {
                        return event instanceof CamelEvent.CamelContextStartedEvent
                                || event instanceof CamelEvent.RouteReloadedEvent;
                    }

                    @Override
                    public void notify(CamelEvent event) {
                        // when reloading then there may be more routes in the same batch, so we only want
                        // to log the summary at the end
                        if (event instanceof CamelEvent.RouteReloadedEvent) {
                            CamelEvent.RouteReloadedEvent re = (CamelEvent.RouteReloadedEvent) event;
                            if (re.getIndex() < re.getTotal()) {
                                return;
                            }
                        }

                        logSummary();
                    }
                });
            }
        });
    }

    protected void setupHealthCheckConsole() {
        final Route health = router.route("/q/health");
        health.method(HttpMethod.GET);
        health.produces("application/json");
        final Route live = router.route("/q/health/live");
        live.method(HttpMethod.GET);
        live.produces("application/json");
        final Route ready = router.route("/q/health/ready");
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

        platformHttpComponent.addHttpEndpoint("/q/health", null, null,
                "application/json", null);
    }

    protected void setupJolokia() {
        // load plugin
        jolokiaPlugin = pluginRegistry.resolvePluginById(JolokiaPlatformHttpPlugin.NAME, JolokiaPlatformHttpPlugin.class)
                .orElseThrow(() -> new RuntimeException(
                        "JolokiaPlatformHttpPlugin not found. Please add camel-platform-http-jolokia dependency."));

        Route jolokia = router.route("/q/jolokia/*");
        jolokia.method(HttpMethod.GET);
        jolokia.method(HttpMethod.POST);

        Handler<RoutingContext> handler = (Handler<RoutingContext>) jolokiaPlugin.getHandler();
        jolokia.handler(new BlockingHandlerDecorator(handler, true));

        platformHttpComponent.addHttpEndpoint("/q/jolokia", null, null,
                "text/plain,application/json", null);
    }

    protected PlatformHttpPluginRegistry resolvePlatformHttpPluginRegistry() {
        Optional<PlatformHttpPluginRegistry> result = ResolverHelper.resolveService(
                getCamelContext(),
                PlatformHttpPluginRegistry.FACTORY,
                PlatformHttpPluginRegistry.class);
        return result.orElseThrow(() -> new IllegalArgumentException(
                "Cannot create PlatformHttpPluginRegistry. Make sure camel-platform-http JAR is on classpath."));
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopAndShutdownService(server);
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
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));

        String trace = sw.toString();
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
                if (dcr == null || !dcr.isEnabled()) {
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

        platformHttpComponent.addHttpEndpoint("/q/dev", null, null,
                "text/plain,application/json", null);
    }

    protected void setupUploadConsole(final String dir) {
        final Route upload = router.route("/q/upload/:filename")
                .method(HttpMethod.PUT)
                // need body handler to handle file uploads
                .handler(BodyHandler.create(true));

        final Route uploadDelete = router.route("/q/upload/:filename");
        uploadDelete.method(HttpMethod.DELETE);

        Handler<RoutingContext> handler = new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext ctx) {
                String name = ctx.pathParam("filename");
                if (name == null) {
                    ctx.response().setStatusCode(400);
                    ctx.end();
                    return;
                }

                int status = 200;
                boolean delete = HttpMethod.DELETE == ctx.request().method();
                if (delete) {
                    if (name.contains("*")) {
                        if (name.equals("*")) {
                            name = "**";
                        }
                        AntPathMatcher match = AntPathMatcher.INSTANCE;
                        File[] files = new File(dir).listFiles();
                        if (files != null) {
                            for (File f : files) {
                                if (f.getName().startsWith(".") || f.isHidden()) {
                                    continue;
                                }
                                if (match.match(name, f.getName())) {
                                    LOG.info("Deleting file: {}/{}", dir, name);
                                    FileUtil.deleteFile(f);
                                }
                            }
                        }
                    } else {
                        File f = new File(dir, name);
                        if (f.exists() && f.isFile()) {
                            LOG.info("Deleting file: {}/{}", dir, name);
                            FileUtil.deleteFile(f);
                        }
                    }
                } else {
                    File f = new File(dir, name);
                    boolean exists = f.isFile() && f.exists();
                    LOG.info("{} file: {}/{}", exists ? "Updating" : "Creating", dir, name);

                    File tmp = new File(dir, name + ".tmp");
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(tmp, false);
                        RequestBody rb = ctx.body();
                        IOHelper.writeText(rb.asString(), fos);

                        FileUtil.renameFileUsingCopy(tmp, f);
                        FileUtil.deleteFile(tmp);
                    } catch (Exception e) {
                        // some kind of error
                        LOG.warn("Error saving file: {}/{} due to: {}", dir, name, e.getMessage(), e);
                        status = 500;
                    } finally {
                        IOHelper.close(fos);
                        FileUtil.deleteFile(tmp);
                    }
                }

                ctx.response().setStatusCode(status);
                ctx.end();
            }
        };
        // use blocking handler as the task can take longer time to complete
        upload.handler(new BlockingHandlerDecorator(handler, true));
        uploadDelete.handler(new BlockingHandlerDecorator(handler, true));

        platformHttpComponent.addHttpEndpoint("/q/upload", "PUT,DELETE",
                "multipart/form-data", null, null);
    }

}
