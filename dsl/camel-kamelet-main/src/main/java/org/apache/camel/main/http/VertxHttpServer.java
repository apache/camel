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
package org.apache.camel.main.http;

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
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.impl.BlockingHandlerDecorator;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StartupListener;
import org.apache.camel.component.platform.http.HttpEndpointModel;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.main.util.CamelJBangSettingsHelper;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To setup vertx http server in the running Camel application
 */
public final class VertxHttpServer {

    static VertxPlatformHttpRouter router;
    static VertxPlatformHttpServer server;
    static PlatformHttpComponent phc;

    private static final Logger LOG = LoggerFactory.getLogger(VertxHttpServer.class);
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean CONSOLE = new AtomicBoolean();
    private static final AtomicBoolean HEALTH_CHECK = new AtomicBoolean();
    private static final AtomicBoolean UPLOAD = new AtomicBoolean();

    private VertxHttpServer() {
    }

    public static void setPlatformHttpComponent(PlatformHttpComponent phc) {
        VertxHttpServer.phc = phc;
    }

    public static void registerServer(CamelContext camelContext, boolean stub) {
        if (REGISTERED.compareAndSet(false, true)) {
            doRegisterServer(camelContext, 8080, stub);
        }
    }

    public static void registerServer(CamelContext camelContext, int port, boolean stub) {
        if (REGISTERED.compareAndSet(false, true)) {
            doRegisterServer(camelContext, port, stub);
        }
    }

    private static void doRegisterServer(CamelContext camelContext, int port, boolean stub) {
        // need to capture we use http-server
        CamelJBangSettingsHelper.writeSettings("camel.jbang.platform-http.port", "" + port);

        if (stub) {
            return;
        }

        try {
            VertxPlatformHttpServerConfiguration config = new VertxPlatformHttpServerConfiguration();
            config.setPort(port);
            server = new VertxPlatformHttpServer(config);
            camelContext.addService(server);
            server.start();
            router = VertxPlatformHttpRouter.lookup(camelContext);
            if (phc == null) {
                phc = camelContext.getComponent("platform-http", PlatformHttpComponent.class);
            }

            // after camel is started then add event notifier
            camelContext.addStartupListener(new StartupListener() {
                @Override
                public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
                    camelContext.getManagementStrategy().addEventNotifier(new SimpleEventNotifierSupport() {

                        private Set<HttpEndpointModel> last;

                        @Override
                        public boolean isEnabled(CamelEvent event) {
                            return event instanceof CamelEvent.CamelContextStartedEvent
                                    || event instanceof CamelEvent.RouteReloadedEvent;
                        }

                        @Override
                        public void notify(CamelEvent event) throws Exception {
                            // when reloading then there may be more routes in the same batch, so we only want
                            // to log the summary at the end
                            if (event instanceof CamelEvent.RouteReloadedEvent) {
                                CamelEvent.RouteReloadedEvent re = (CamelEvent.RouteReloadedEvent) event;
                                if (re.getIndex() < re.getTotal()) {
                                    return;
                                }
                            }

                            Set<HttpEndpointModel> endpoints = phc.getHttpEndpoints();
                            if (endpoints.isEmpty()) {
                                return;
                            }

                            // log only if changed
                            if (last == null || last.size() != endpoints.size() || !last.containsAll(endpoints)) {
                                LOG.info("HTTP endpoints summary");
                                for (HttpEndpointModel u : endpoints) {
                                    String line = "http://0.0.0.0:" + port + u.getUri();
                                    if (u.getVerbs() != null) {
                                        line += " (" + u.getVerbs() + ")";
                                    }
                                    LOG.info("    {}", line);
                                }
                            }

                            // use a defensive copy of last known endpoints
                            last = new HashSet<>(endpoints);
                        }
                    });
                }
            });

        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }

    public static void registerConsole(CamelContext camelContext) {
        if (CONSOLE.compareAndSet(false, true)) {
            doRegisterConsole(camelContext);
        }
    }

    private static void doRegisterConsole(CamelContext context) {
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

                DevConsoleRegistry dcr = context.getExtension(DevConsoleRegistry.class);
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
                    if (sb.length() > 0) {
                        String out = sb.toString();
                        if (html) {
                            ctx.response().putHeader("content-type", "text/html");
                        }
                        ctx.end(out);
                    } else if (!root.isEmpty()) {
                        ctx.response().putHeader("content-type", "application/json");
                        String out = root.toJson();
                        ctx.end(out);
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
                    if (sb.length() > 0) {
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

        phc.addHttpEndpoint("/q/dev", null);
    }

    public static void registerHealthCheck(CamelContext camelContext) {
        if (HEALTH_CHECK.compareAndSet(false, true)) {
            doRegisterHealthCheck(camelContext);
        }
    }

    private static void doRegisterHealthCheck(CamelContext context) {
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

                boolean all = ctx.currentRoute() == health;
                boolean liv = ctx.currentRoute() == live;
                boolean rdy = ctx.currentRoute() == ready;

                Collection<HealthCheck.Result> res;
                if (all) {
                    res = HealthCheckHelper.invoke(context);
                } else if (liv) {
                    res = HealthCheckHelper.invokeLiveness(context);
                } else {
                    res = HealthCheckHelper.invokeReadiness(context);
                }

                StringBuilder sb = new StringBuilder();
                sb.append("{\n");

                HealthCheckRegistry registry = HealthCheckRegistry.get(context);
                String level = ctx.request().getParam("exposureLevel");
                if (level == null) {
                    level = registry.getExposureLevel();
                }

                // are we UP
                boolean up = HealthCheckHelper.isResultsUp(res, rdy);

                if ("oneline".equals(level)) {
                    // only brief status
                    healthCheckStatus(sb, up);
                } else if ("full".equals(level)) {
                    // include all details
                    List<HealthCheck.Result> list = new ArrayList<>(res);
                    healthCheckDetails(sb, list, up);
                } else {
                    // include only DOWN details
                    List<HealthCheck.Result> downs = res.stream().filter(r -> r.getState().equals(HealthCheck.State.DOWN))
                            .collect(Collectors.toList());
                    healthCheckDetails(sb, downs, up);
                }
                sb.append("}\n");

                if (!up) {
                    // we need to fail with a http status so lets use 500
                    ctx.response().setStatusCode(500);
                }
                ctx.end(sb.toString());
            }
        };
        // use blocking handler as the task can take longer time to complete
        health.handler(new BlockingHandlerDecorator(handler, true));
        live.handler(new BlockingHandlerDecorator(handler, true));
        ready.handler(new BlockingHandlerDecorator(handler, true));

        phc.addHttpEndpoint("/q/health", null);
    }

    private static void healthCheckStatus(StringBuilder sb, boolean up) {
        if (up) {
            sb.append("    \"status\": \"UP\"\n");
        } else {
            sb.append("    \"status\": \"DOWN\"\n");
        }
    }

    private static void healthCheckDetails(StringBuilder sb, List<HealthCheck.Result> checks, boolean up) {
        healthCheckStatus(sb, up);

        if (!checks.isEmpty()) {
            sb.append(",\n");
            sb.append("    \"checks\": [\n");
            for (int i = 0; i < checks.size(); i++) {
                HealthCheck.Result d = checks.get(i);
                sb.append("        {\n");
                reportHealthCheck(sb, d);
                if (i < checks.size() - 1) {
                    sb.append("        },\n");
                } else {
                    sb.append("        }\n");
                }
            }
            sb.append("    ]\n");
        }
    }

    private static void reportHealthCheck(StringBuilder sb, HealthCheck.Result d) {
        sb.append("            \"name\": \"").append(d.getCheck().getId()).append("\",\n");
        sb.append("            \"status\": \"").append(d.getState()).append("\",\n");
        if (d.getError().isPresent()) {
            String msg = allCausedByErrorMessages(d.getError().get());
            sb.append("            \"error-message\": \"").append(msg)
                    .append("\",\n");
            sb.append("            \"error-stacktrace\": \"").append(errorStackTrace(d.getError().get()))
                    .append("\",\n");
        }
        if (d.getMessage().isPresent()) {
            sb.append("            \"message\": \"").append(d.getMessage().get()).append("\",\n");
        }
        if (d.getDetails() != null && !d.getDetails().isEmpty()) {
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

    public static void registerUploadSourceDir(CamelContext camelContext, String dir) {
        if (UPLOAD.compareAndSet(false, true)) {
            doRegisterUploadSourceDir(camelContext, dir);
        }
    }

    private static void doRegisterUploadSourceDir(CamelContext context, final String dir) {
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

        phc.addHttpEndpoint("/q/upload", "PUT,DELETE");
    }

}
