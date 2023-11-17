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
package org.apache.camel.component.micrometer.prometheus;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.BlockingHandlerDecorator;
import org.apache.camel.CamelContext;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.micrometer.MicrometerUtils;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerExchangeEventNotifier;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerExchangeEventNotifierNamingStrategy;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerRouteEventNotifier;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerRouteEventNotifierNamingStrategy;
import org.apache.camel.component.micrometer.messagehistory.MicrometerMessageHistoryFactory;
import org.apache.camel.component.micrometer.messagehistory.MicrometerMessageHistoryNamingStrategy;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyNamingStrategy;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpRouter;
import org.apache.camel.spi.CamelMetricsService;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService("micrometer-prometheus")
@Configurer
@ManagedResource(description = "Micrometer Metrics Prometheus")
public class MicrometerPrometheus extends ServiceSupport implements CamelMetricsService, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(MicrometerPrometheus.class);

    private MainHttpServer server;
    private VertxPlatformHttpRouter router;
    private PlatformHttpComponent platformHttpComponent;

    private CamelContext camelContext;
    private PrometheusMeterRegistry meterRegistry;
    private final Set<MeterBinder> createdBinders = new HashSet<>();

    @Metadata(defaultValue = "default", enums = "default,legacy")
    private String namingStrategy;
    @Metadata(defaultValue = "true")
    private boolean enableRoutePolicy = true;
    @Metadata(defaultValue = "false")
    private boolean enableMessageHistory;
    @Metadata(defaultValue = "true")
    private boolean enableExchangeEventNotifier = true;
    @Metadata(defaultValue = "true")
    private boolean enableRouteEventNotifier = true;
    @Metadata(defaultValue = "0.0.4", enums = "0.0.4,1.0.0")
    private String textFormatVersion = "0.0.4";
    @Metadata
    private String binders;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getNamingStrategy() {
        return namingStrategy;
    }

    /**
     * Controls the name style to use for metrics.
     *
     * Default = uses micrometer naming convention. Legacy = uses the classic naming style (camelCase)
     */
    public void setNamingStrategy(String namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    public boolean isEnableRoutePolicy() {
        return enableRoutePolicy;
    }

    /**
     * Set whether to enable the MicrometerRoutePolicyFactory for capturing metrics on route processing times.
     */
    public void setEnableRoutePolicy(boolean enableRoutePolicy) {
        this.enableRoutePolicy = enableRoutePolicy;
    }

    public boolean isEnableMessageHistory() {
        return enableMessageHistory;
    }

    /**
     * Set whether to enable the MicrometerMessageHistoryFactory for capturing metrics on individual route node
     * processing times.
     *
     * Depending on the number of configured route nodes, there is the potential to create a large volume of metrics.
     * Therefore, this option is disabled by default.
     */
    public void setEnableMessageHistory(boolean enableMessageHistory) {
        this.enableMessageHistory = enableMessageHistory;
    }

    public boolean isEnableExchangeEventNotifier() {
        return enableExchangeEventNotifier;
    }

    /**
     * Set whether to enable the MicrometerExchangeEventNotifier for capturing metrics on exchange processing times.
     */
    public void setEnableExchangeEventNotifier(boolean enableExchangeEventNotifier) {
        this.enableExchangeEventNotifier = enableExchangeEventNotifier;
    }

    public boolean isEnableRouteEventNotifier() {
        return enableRouteEventNotifier;
    }

    /**
     * Set whether to enable the MicrometerRouteEventNotifier for capturing metrics on the total number of routes and
     * total number of routes running.
     */
    public void setEnableRouteEventNotifier(boolean enableRouteEventNotifier) {
        this.enableRouteEventNotifier = enableRouteEventNotifier;
    }

    public String getTextFormatVersion() {
        return textFormatVersion;
    }

    /**
     * The text-format version to use with Prometheus scraping.
     *
     * 0.0.4 = text/plain; version=0.0.4; charset=utf-8 1.0.0 = application/openmetrics-text; version=1.0.0;
     * charset=utf-8
     */
    public void setTextFormatVersion(String textFormatVersion) {
        this.textFormatVersion = textFormatVersion;
    }

    public String getBinders() {
        return binders;
    }

    /**
     * Additional Micrometer binders to include such as jvm-memory, processor, jvm-thread, and so forth. Multiple
     * binders can be separated by comma.
     *
     * The following binders currently is available from Micrometer: class-loader, commons-object-pool2,
     * file-descriptor, hystrix-metrics-binder, jvm-compilation, jvm-gc, jvm-heap-pressure, jvm-info, jvm-memory,
     * jvm-thread, log4j2, logback, processor, uptime
     */
    public void setBinders(String binders) {
        this.binders = binders;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (meterRegistry == null) {
            Registry camelRegistry = getCamelContext().getRegistry();
            MeterRegistry found = MicrometerUtils.getMeterRegistryFromCamelRegistry(camelRegistry,
                    MicrometerConstants.METRICS_REGISTRY_NAME);
            if (found == null) {
                found = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
                // enlist in registry so it can be reused
                camelRegistry.bind(MicrometerConstants.METRICS_REGISTRY_NAME, found);
            }
            if (!(found instanceof PrometheusMeterRegistry)) {
                throw new IllegalArgumentException(
                        "Existing MeterRegistry: " + found.getClass().getName() + " is not a PrometheusMeterRegistry type.");
            }
            meterRegistry = (PrometheusMeterRegistry) found;
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (ObjectHelper.isNotEmpty(binders)) {
            // load binders from micrometer
            initBinders();
        }

        if (isEnableRoutePolicy()) {
            MicrometerRoutePolicyFactory factory = new MicrometerRoutePolicyFactory();
            if ("legacy".equalsIgnoreCase(namingStrategy)) {
                factory.setNamingStrategy(MicrometerRoutePolicyNamingStrategy.LEGACY);
            }
            factory.setMeterRegistry(meterRegistry);
            camelContext.addRoutePolicyFactory(factory);
        }

        ManagementStrategy managementStrategy = camelContext.getManagementStrategy();
        if (isEnableExchangeEventNotifier()) {
            MicrometerExchangeEventNotifier notifier = new MicrometerExchangeEventNotifier();
            if ("legacy".equalsIgnoreCase(namingStrategy)) {
                notifier.setNamingStrategy(MicrometerExchangeEventNotifierNamingStrategy.LEGACY);
            }
            notifier.setMeterRegistry(meterRegistry);
            managementStrategy.addEventNotifier(notifier);
        }

        if (isEnableRouteEventNotifier()) {
            MicrometerRouteEventNotifier notifier = new MicrometerRouteEventNotifier();
            if ("legacy".equalsIgnoreCase(namingStrategy)) {
                notifier.setNamingStrategy(MicrometerRouteEventNotifierNamingStrategy.LEGACY);
            }
            notifier.setMeterRegistry(meterRegistry);
            managementStrategy.addEventNotifier(notifier);
        }

        if (isEnableMessageHistory()) {
            if (!camelContext.isMessageHistory()) {
                camelContext.setMessageHistory(true);
            }
            MicrometerMessageHistoryFactory factory = new MicrometerMessageHistoryFactory();
            if ("legacy".equalsIgnoreCase(namingStrategy)) {
                factory.setNamingStrategy(MicrometerMessageHistoryNamingStrategy.LEGACY);
            }
            factory.setMeterRegistry(meterRegistry);
            camelContext.setMessageHistoryFactory(factory);
        }

        server = camelContext.hasService(MainHttpServer.class);
        router = VertxPlatformHttpRouter.lookup(camelContext);
        platformHttpComponent = camelContext.getComponent("platform-http", PlatformHttpComponent.class);

        if (server != null && server.isMetricsEnabled() && router != null && platformHttpComponent != null) {
            setupHttpScraper();
            LOG.info("MicrometerPrometheus enabled with HTTP scraping on /q/metrics");
        } else {
            LOG.info("MicrometerPrometheus enabled");
        }
    }

    private void initBinders() throws IOException {
        List<String> names = BindersHelper.discoverBinders(camelContext.getClassResolver(), binders);
        List<MeterBinder> binders = BindersHelper.loadBinders(camelContext, names);

        StringJoiner sj = new StringJoiner(", ");
        for (MeterBinder mb : binders) {
            mb.bindTo(meterRegistry);
            createdBinders.add(mb);
            sj.add(mb.getClass().getSimpleName());
        }
        if (!createdBinders.isEmpty()) {
            LOG.info("Registered {} MeterBinders: {}", createdBinders.size(), sj);
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();

        for (MeterBinder mb : createdBinders) {
            if (mb instanceof Closeable ac) {
                IOHelper.close(ac);
            }
        }
        createdBinders.clear();
    }

    protected void setupHttpScraper() {
        Route metrics = router.route("/q/metrics");
        metrics.method(HttpMethod.GET);

        final String format
                = "0.0.4".equals(textFormatVersion) ? TextFormat.CONTENT_TYPE_004 : TextFormat.CONTENT_TYPE_OPENMETRICS_100;
        metrics.produces(format);

        Handler<RoutingContext> handler = new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext ctx) {
                String ct = format;
                // the client may ask for version 1.0.0 via accept header
                String ah = ctx.request().getHeader("Accept");
                if (ah != null && ah.contains("application/openmetrics-text")) {
                    ct = TextFormat.chooseContentType(ah);
                }

                ctx.response().putHeader("Content-Type", ct);
                String data = meterRegistry.scrape(ct);
                ctx.end(data);
            }
        };

        // use blocking handler as the task can take longer time to complete
        metrics.handler(new BlockingHandlerDecorator(handler, true));

        platformHttpComponent.addHttpEndpoint("/q/metrics", null, null);
    }
}
