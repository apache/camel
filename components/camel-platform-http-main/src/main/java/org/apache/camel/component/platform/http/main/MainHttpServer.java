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
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Optional;

import io.vertx.core.Handler;
import io.vertx.core.http.impl.MimeMapping;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.BlockingHandlerDecorator;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.platform.http.spi.PlatformHttpPluginRegistry;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Camel Main Embedded HTTP server")
public class MainHttpServer extends ServiceSupport implements CamelContextAware, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(MainHttpServer.class);

    private VertxPlatformHttpServer server;
    private VertxPlatformHttpRouter router;

    private CamelContext camelContext;
    private PlatformHttpComponent platformHttpComponent;

    private VertxPlatformHttpServerConfiguration configuration = new VertxPlatformHttpServerConfiguration();
    private boolean staticEnabled;
    private String staticSourceDir;
    private String staticContextPath;

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

    @ManagedAttribute(description = "Whether serving static content is enabled (such as html pages)")
    public boolean isStaticEnabled() {
        return staticEnabled;
    }

    public void setStaticEnabled(boolean staticEnabled) {
        this.staticEnabled = staticEnabled;
    }

    public String getStaticSourceDir() {
        return staticSourceDir;
    }

    @ManagedAttribute(description = "The source dir for serving static content")
    public void setStaticSourceDir(String staticSourceDir) {
        this.staticSourceDir = staticSourceDir;
    }

    @ManagedAttribute(description = "The context-path for serving static content")
    public String getStaticContextPath() {
        return staticContextPath;
    }

    public void setStaticContextPath(String staticContextPath) {
        this.staticContextPath = staticContextPath;
    }

    @ManagedAttribute(description = "Whether serving static content is enabled (such as html pages)")
    public boolean isStaticFilePattern() {
        return staticEnabled;
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

        // Mark this as the main server (not management) so the engine can identify it
        configuration.setServerType(VertxPlatformHttpRouter.SERVER_TYPE_SERVER);

        server = new VertxPlatformHttpServer(configuration);
        // adding server to camel-context which will manage shutdown the server, so we should not do this here
        camelContext.addService(server);
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");
        ServiceHelper.startService(server);
        String routerName = VertxPlatformHttpRouter.getRouterNameFromPort(getPort());
        router = VertxPlatformHttpRouter.lookup(camelContext, routerName);
        platformHttpComponent = camelContext.getComponent("platform-http", PlatformHttpComponent.class);

        setupConsoles();
        setupStartupSummary();
    }

    protected void setupConsoles() {
        if (staticEnabled) {
            setupStatic();
        }
    }

    protected void setupStatic() {
        String path = staticContextPath;
        if (!path.endsWith("*")) {
            path = path + "*";
        }
        final Route web = router.route(path);
        web.produces("*");
        web.consumes("*");
        web.order(Integer.MAX_VALUE); // run this last so all other are served first

        Handler<RoutingContext> handler = new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext ctx) {
                String u = ctx.normalizedPath();
                if (u.isBlank() || u.endsWith("/") || u.equals("index.html")) {
                    u = "index.html";
                } else {
                    u = FileUtil.stripLeadingSeparator(u);
                }

                InputStream is = null;
                File f = new File(u);
                if (!f.exists() && staticSourceDir != null) {
                    f = new File(staticSourceDir, u);
                }
                if (f.exists()) {
                    // load directly from file system first
                    try {
                        is = new FileInputStream(f);
                    } catch (Exception e) {
                        // ignore
                    }
                } else {
                    is = camelContext.getClassResolver().loadResourceAsStream(u);
                    if (is == null) {
                        // common folder for java app servers like quarkus and spring-boot
                        is = camelContext.getClassResolver().loadResourceAsStream("META-INF/resources/" + u);
                    }
                    if (is == null && staticSourceDir != null) {
                        is = camelContext.getClassResolver().loadResourceAsStream(staticSourceDir + "/" + u);
                    }
                }
                if (is != null) {
                    String mime = MimeMapping.getMimeTypeForFilename(f.getName());
                    if (mime != null) {
                        ctx.response().putHeader("content-type", mime);
                    }
                    String text = null;
                    try {
                        text = IOHelper.loadText(is);
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        IOHelper.close(is);
                    }
                    ctx.response().setStatusCode(200);
                    ctx.end(text);
                } else {
                    ctx.response().setStatusCode(404);
                    ctx.end();
                }
            }
        };

        // use blocking handler as the task can take longer time to complete
        web.handler(new BlockingHandlerDecorator(handler, true));

        platformHttpComponent.addHttpEndpoint(staticContextPath, null, null, null, null);
    }

    protected PlatformHttpPluginRegistry resolvePlatformHttpPluginRegistry() {
        Optional<PlatformHttpPluginRegistry> result = ResolverHelper.resolveService(
                getCamelContext(),
                PlatformHttpPluginRegistry.FACTORY,
                PlatformHttpPluginRegistry.class);
        return result.orElseThrow(() -> new IllegalArgumentException(
                "Cannot create PlatformHttpPluginRegistry. Make sure camel-platform-http JAR is on classpath."));
    }

    protected void setupStartupSummary() throws Exception {
        MainHttpServerUtil.setupStartupSummary(
                camelContext,
                platformHttpComponent.getHttpEndpoints(),
                (server != null ? server.getPort() : getPort()),
                "HTTP endpoints summary");
    }

}
