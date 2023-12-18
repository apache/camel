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
package org.apache.camel.main;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.apache.camel.CamelConfiguration;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.LambdaRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;

/**
 * Global configuration for Camel Main to configure context name, stream caching and other global configurations.
 */
@Configurer(bootstrap = true)
public class MainConfigurationProperties extends DefaultConfigurationProperties<MainConfigurationProperties>
        implements BootstrapCloseable {

    private boolean autoConfigurationEnabled = true;
    private boolean autoConfigurationEnvironmentVariablesEnabled = true;
    private boolean autoConfigurationSystemPropertiesEnabled = true;
    private boolean autoConfigurationFailFast = true;
    private boolean autoConfigurationLogSummary = true;
    private int durationHitExitCode;
    private int extraShutdownTimeout = 15;
    private String basePackageScan;
    private boolean basePackageScanEnabled = true;

    private String routesBuilderClasses;
    private String configurationClasses;

    private List<RoutesBuilder> routesBuilders = new ArrayList<>();
    private List<CamelConfiguration> configurations = new ArrayList<>();

    // extended configuration
    private HealthConfigurationProperties healthConfigurationProperties;
    private LraConfigurationProperties lraConfigurationProperties;
    private OtelConfigurationProperties otelConfigurationProperties;
    private MetricsConfigurationProperties metricsConfigurationProperties;
    private ThreadPoolConfigurationProperties threadPoolProperties;
    private Resilience4jConfigurationProperties resilience4jConfigurationProperties;
    private FaultToleranceConfigurationProperties faultToleranceConfigurationProperties;
    private RestConfigurationProperties restConfigurationProperties;
    private VaultConfigurationProperties vaultConfigurationProperties;
    private HttpServerConfigurationProperties httpServerConfigurationProperties;
    private SSLConfigurationProperties sslConfigurationProperties;
    private DebuggerConfigurationProperties debuggerConfigurationProperties;
    private RouteControllerConfigurationProperties routeControllerConfigurationProperties;

    @Override
    public void close() {
        if (healthConfigurationProperties != null) {
            healthConfigurationProperties.close();
            healthConfigurationProperties = null;
        }
        if (lraConfigurationProperties != null) {
            lraConfigurationProperties.close();
            lraConfigurationProperties = null;
        }
        if (otelConfigurationProperties != null) {
            otelConfigurationProperties.close();
            otelConfigurationProperties = null;
        }
        if (metricsConfigurationProperties != null) {
            metricsConfigurationProperties.close();
            metricsConfigurationProperties = null;
        }
        if (threadPoolProperties != null) {
            threadPoolProperties.close();
            threadPoolProperties = null;
        }
        if (resilience4jConfigurationProperties != null) {
            resilience4jConfigurationProperties.close();
            resilience4jConfigurationProperties = null;
        }
        if (faultToleranceConfigurationProperties != null) {
            faultToleranceConfigurationProperties.close();
            faultToleranceConfigurationProperties = null;
        }
        if (restConfigurationProperties != null) {
            restConfigurationProperties.close();
            restConfigurationProperties = null;
        }
        if (vaultConfigurationProperties != null) {
            vaultConfigurationProperties.close();
            vaultConfigurationProperties = null;
        }
        if (httpServerConfigurationProperties != null) {
            httpServerConfigurationProperties.close();
            httpServerConfigurationProperties = null;
        }
        if (sslConfigurationProperties != null) {
            sslConfigurationProperties.close();
            sslConfigurationProperties = null;
        }
        if (debuggerConfigurationProperties != null) {
            debuggerConfigurationProperties.close();
            debuggerConfigurationProperties = null;
        }
        if (routeControllerConfigurationProperties != null) {
            routeControllerConfigurationProperties.close();
            routeControllerConfigurationProperties = null;
        }
        if (routesBuilders != null) {
            routesBuilders.clear();
            routesBuilders = null;
        }
        if (configurations != null) {
            configurations.clear();
            configurations = null;
        }
    }

    // extended
    // --------------------------------------------------------------

    /**
     * To configure Health Check
     */
    public HealthConfigurationProperties health() {
        if (healthConfigurationProperties == null) {
            healthConfigurationProperties = new HealthConfigurationProperties(this);
        }
        return healthConfigurationProperties;
    }

    /**
     * Whether there has been any health check configuration specified
     */
    public boolean hasHealthCheckConfiguration() {
        return healthConfigurationProperties != null;
    }

    /**
     * To configure Saga LRA
     */
    public LraConfigurationProperties lra() {
        if (lraConfigurationProperties == null) {
            lraConfigurationProperties = new LraConfigurationProperties(this);
        }
        return lraConfigurationProperties;
    }

    /**
     * Whether there has been any Saga LRA configuration specified
     */
    public boolean hasLraConfiguration() {
        return lraConfigurationProperties != null;
    }

    /**
     * To configure OpenTelemetry.
     */
    public OtelConfigurationProperties otel() {
        if (otelConfigurationProperties == null) {
            otelConfigurationProperties = new OtelConfigurationProperties(this);
        }
        return otelConfigurationProperties;
    }

    /**
     * Whether there has been any OpenTelemetry configuration specified
     */
    public boolean hasOtelConfiguration() {
        return otelConfigurationProperties != null;
    }

    /**
     * To configure Micrometer metrics.
     */
    public MetricsConfigurationProperties metrics() {
        if (metricsConfigurationProperties == null) {
            metricsConfigurationProperties = new MetricsConfigurationProperties(this);
        }
        return metricsConfigurationProperties;
    }

    /**
     * Whether there has been any Micrometer metrics configuration specified
     */
    public boolean hasMetricsConfiguration() {
        return metricsConfigurationProperties != null;
    }

    /**
     * To configure embedded HTTP server (for standalone applications; not Spring Boot or Quarkus)
     */
    public HttpServerConfigurationProperties httpServer() {
        if (httpServerConfigurationProperties == null) {
            httpServerConfigurationProperties = new HttpServerConfigurationProperties(this);
        }
        return httpServerConfigurationProperties;
    }

    /**
     * Whether there has been any embedded HTTP server configuration specified
     */
    public boolean hasHttpServerConfiguration() {
        return httpServerConfigurationProperties != null;
    }

    /**
     * To configure SSL.
     */
    public SSLConfigurationProperties sslConfig() {
        if (sslConfigurationProperties == null) {
            sslConfigurationProperties = new SSLConfigurationProperties(this);
        }

        return sslConfigurationProperties;
    }

    /**
     * Whether there has been any SSL configuration specified.
     */
    public boolean hasSslConfiguration() {
        return sslConfigurationProperties != null;
    }

    /**
     * To configure Debugger.
     */
    public DebuggerConfigurationProperties debuggerConfig() {
        if (debuggerConfigurationProperties == null) {
            debuggerConfigurationProperties = new DebuggerConfigurationProperties(this);
        }

        return debuggerConfigurationProperties;
    }

    /**
     * Whether there has been any Debugger configuration specified.
     */
    public boolean hasDebuggerConfiguration() {
        return debuggerConfigurationProperties != null;
    }

    /**
     * To configure Route Controller.
     */
    public RouteControllerConfigurationProperties routeControllerConfig() {
        if (routeControllerConfigurationProperties == null) {
            routeControllerConfigurationProperties = new RouteControllerConfigurationProperties(this);
        }

        return routeControllerConfigurationProperties;
    }

    /**
     * Whether there has been any Route Controller configuration specified.
     */
    public boolean hasRouteControllerConfiguration() {
        return routeControllerConfigurationProperties != null;
    }

    /**
     * To configure thread pools
     */
    public ThreadPoolConfigurationProperties threadPool() {
        if (threadPoolProperties == null) {
            threadPoolProperties = new ThreadPoolConfigurationProperties(this);
        }
        return threadPoolProperties;
    }

    /**
     * Whether there has been any thread pool configuration specified
     */
    public boolean hasThreadPoolConfiguration() {
        return threadPoolProperties != null;
    }

    /**
     * To configure Circuit Breaker EIP with Resilience4j
     */
    public Resilience4jConfigurationProperties resilience4j() {
        if (resilience4jConfigurationProperties == null) {
            resilience4jConfigurationProperties = new Resilience4jConfigurationProperties(this);
        }
        return resilience4jConfigurationProperties;
    }

    /**
     * Whether there has been any Resilience4j EIP configuration specified
     */
    public boolean hasResilience4jConfiguration() {
        return resilience4jConfigurationProperties != null;
    }

    /**
     * To configure Circuit Breaker EIP with MicroProfile Fault Tolerance
     */
    public FaultToleranceConfigurationProperties faultTolerance() {
        if (faultToleranceConfigurationProperties == null) {
            faultToleranceConfigurationProperties = new FaultToleranceConfigurationProperties(this);
        }
        return faultToleranceConfigurationProperties;
    }

    /**
     * Whether there has been any MicroProfile Fault Tolerance EIP configuration specified
     */
    public boolean hasFaultToleranceConfiguration() {
        return faultToleranceConfigurationProperties != null;
    }

    /**
     * To configure Rest DSL
     */
    public RestConfigurationProperties rest() {
        if (restConfigurationProperties == null) {
            restConfigurationProperties = new RestConfigurationProperties(this);
        }
        return restConfigurationProperties;
    }

    /**
     * Whether there has been any rest configuration specified
     */
    public boolean hasRestConfiguration() {
        return restConfigurationProperties != null;
    }

    /**
     * To configure access to AWS vaults
     */
    public VaultConfigurationProperties vault() {
        if (vaultConfigurationProperties == null) {
            vaultConfigurationProperties = new VaultConfigurationProperties(this);
        }
        return vaultConfigurationProperties;
    }

    /**
     * Whether there has been any vault configuration specified
     */
    public boolean hasVaultConfiguration() {
        return vaultConfigurationProperties != null;
    }

    // getter and setters
    // --------------------------------------------------------------

    public boolean isAutoConfigurationEnabled() {
        return autoConfigurationEnabled;
    }

    /**
     * Whether auto configuration of components, dataformats, languages is enabled or not. When enabled the
     * configuration parameters are loaded from the properties component. You can prefix the parameters in the
     * properties file with: - camel.component.name.option1=value1 - camel.component.name.option2=value2 -
     * camel.dataformat.name.option1=value1 - camel.dataformat.name.option2=value2 - camel.language.name.option1=value1
     * - camel.language.name.option2=value2 Where name is the name of the component, dataformat or language such as
     * seda,direct,jaxb.
     * <p/>
     * The auto configuration also works for any options on components that is a complex type (not standard Java type)
     * and there has been an explicit single bean instance registered to the Camel registry via the
     * {@link org.apache.camel.spi.Registry#bind(String, Object)} method or by using the
     * {@link org.apache.camel.BindToRegistry} annotation style.
     * <p/>
     * This option is default enabled.
     */
    public void setAutoConfigurationEnabled(boolean autoConfigurationEnabled) {
        this.autoConfigurationEnabled = autoConfigurationEnabled;
    }

    public boolean isAutoConfigurationEnvironmentVariablesEnabled() {
        return autoConfigurationEnvironmentVariablesEnabled;
    }

    /**
     * Whether auto configuration should include OS environment variables as well. When enabled this allows to overrule
     * any configuration using an OS environment variable. For example to set a shutdown timeout of 5 seconds:
     * CAMEL_MAIN_SHUTDOWNTIMEOUT=5.
     * <p/>
     * This option is default enabled.
     */
    public void setAutoConfigurationEnvironmentVariablesEnabled(boolean autoConfigurationEnvironmentVariablesEnabled) {
        this.autoConfigurationEnvironmentVariablesEnabled = autoConfigurationEnvironmentVariablesEnabled;
    }

    public boolean isAutoConfigurationSystemPropertiesEnabled() {
        return autoConfigurationSystemPropertiesEnabled;
    }

    /**
     * Whether auto configuration should include JVM system properties as well. When enabled this allows to overrule any
     * configuration using a JVM system property. For example to set a shutdown timeout of 5 seconds: -D
     * camel.main.shutdown-timeout=5.
     * <p/>
     * Note that JVM system properties take precedence over OS environment variables.
     * <p/>
     * This option is default enabled.
     */
    public void setAutoConfigurationSystemPropertiesEnabled(boolean autoConfigurationSystemPropertiesEnabled) {
        this.autoConfigurationSystemPropertiesEnabled = autoConfigurationSystemPropertiesEnabled;
    }

    public boolean isAutoConfigurationFailFast() {
        return autoConfigurationFailFast;
    }

    /**
     * Whether auto configuration should fail fast when configuring one ore more properties fails for whatever reason
     * such as a invalid property name, etc.
     * <p/>
     * This option is default enabled.
     */
    public void setAutoConfigurationFailFast(boolean autoConfigurationFailFast) {
        this.autoConfigurationFailFast = autoConfigurationFailFast;
    }

    public boolean isAutoConfigurationLogSummary() {
        return autoConfigurationLogSummary;
    }

    /**
     * Whether auto configuration should log a summary with the configured properties.
     * <p/>
     * This option is default enabled.
     */
    public void setAutoConfigurationLogSummary(boolean autoConfigurationLogSummary) {
        this.autoConfigurationLogSummary = autoConfigurationLogSummary;
    }

    public String getBasePackageScan() {
        return basePackageScan;
    }

    /**
     * Package name to use as base (offset) for classpath scanning of {@link RouteBuilder},
     * {@link org.apache.camel.TypeConverter}, {@link CamelConfiguration} classes, and also classes annotated with
     * {@link org.apache.camel.Converter}, or {@link org.apache.camel.BindToRegistry}.
     *
     * If you are using Spring Boot then it is instead recommended to use Spring Boots component scanning and annotate
     * your route builder classes with `@Component`. In other words only use this for Camel Main in standalone mode.
     */
    public void setBasePackageScan(String basePackageScan) {
        this.basePackageScan = basePackageScan;
    }

    public boolean isBasePackageScanEnabled() {
        return basePackageScanEnabled;
    }

    /**
     * Whether base package scan is enabled.
     */
    public void setBasePackageScanEnabled(boolean basePackageScanEnabled) {
        this.basePackageScanEnabled = basePackageScanEnabled;
    }

    public int getDurationHitExitCode() {
        return durationHitExitCode;
    }

    /**
     * Sets the exit code for the application if duration was hit
     */
    public void setDurationHitExitCode(int durationHitExitCode) {
        this.durationHitExitCode = durationHitExitCode;
    }

    public int getExtraShutdownTimeout() {
        return extraShutdownTimeout;
    }

    /**
     * Extra timeout in seconds to graceful shutdown Camel.
     *
     * When Camel is shutting down then Camel first shutdown all the routes (shutdownTimeout). Then additional services
     * is shutdown (extraShutdownTimeout).
     */
    public void setExtraShutdownTimeout(int extraShutdownTimeout) {
        this.extraShutdownTimeout = extraShutdownTimeout;
    }

    // getter and setters - configurations
    // --------------------------------------------------------------

    public String getConfigurationClasses() {
        return configurationClasses;
    }

    /**
     * Sets classes names that will be used to configure the camel context as example by providing custom beans through
     * {@link org.apache.camel.BindToRegistry} annotation.
     */
    public void setConfigurationClasses(String configurations) {
        this.configurationClasses = configurations;
    }

    /**
     * Adds configuration object to the known list of configurations objects.
     */
    @SuppressWarnings("unchecked")
    private void addConfigurationClass(Class<? extends CamelConfiguration>... configuration) {
        StringJoiner existing = new StringJoiner(",");
        if (configurationClasses != null && !configurationClasses.isEmpty()) {
            existing.add(configurationClasses);
        }
        if (configuration != null) {
            for (Class<? extends CamelConfiguration> clazz : configuration) {
                existing.add(clazz.getName());
            }
        }
        setConfigurationClasses(existing.toString());
    }

    /**
     * Adds configuration object to the known list of configurations objects.
     */
    public void addConfiguration(CamelConfiguration configuration) {
        configurations.add(configuration);
    }

    /**
     * Adds configuration object to the known list of configurations objects.
     */
    public void addConfiguration(Class<? extends CamelConfiguration> configuration) {
        addConfigurationClass(configuration);
    }

    public List<CamelConfiguration> getConfigurations() {
        return configurations;
    }

    /**
     * Sets the configuration objects used to configure the camel context.
     */
    public void setConfigurations(List<CamelConfiguration> configurations) {
        this.configurations = configurations;
    }

    // getter and setters - routes builders
    // --------------------------------------------------------------

    public String getRoutesBuilderClasses() {
        return routesBuilderClasses;
    }

    /**
     * Sets classes names that implement {@link RoutesBuilder}.
     */
    public void setRoutesBuilderClasses(String builders) {
        this.routesBuilderClasses = builders;
    }

    public List<RoutesBuilder> getRoutesBuilders() {
        return this.routesBuilders;
    }

    /**
     * Sets the RoutesBuilder instances.
     */
    public void setRoutesBuilders(List<RoutesBuilder> routesBuilders) {
        this.routesBuilders = routesBuilders;
    }

    /**
     * Add an additional {@link RoutesBuilder} object to the known list of builders.
     */
    public void addRoutesBuilder(RoutesBuilder routeBuilder) {
        this.routesBuilders.add(routeBuilder);
    }

    /**
     * Add an additional {@link RoutesBuilder} class to the known list of builders.
     */
    public void addRoutesBuilder(Class<?>... routeBuilder) {
        StringJoiner existing = new StringJoiner(",");
        if (routesBuilderClasses != null && !routesBuilderClasses.isEmpty()) {
            existing.add(routesBuilderClasses);
        }
        if (routeBuilder != null) {
            for (Class<?> clazz : routeBuilder) {
                existing.add(clazz.getName());
            }
        }
        setRoutesBuilderClasses(existing.toString());
    }

    /**
     * Add an additional {@link LambdaRouteBuilder} object to the known list of builders.
     */
    public void addLambdaRouteBuilder(LambdaRouteBuilder routeBuilder) {
        this.routesBuilders.add(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeBuilder.accept(this);
            }
        });
    }

    // fluent builders
    // --------------------------------------------------------------

    /**
     * Whether auto configuration of components/dataformats/languages is enabled or not. When enabled the configuration
     * parameters are loaded from the properties component and configured as defaults (similar to spring-boot
     * auto-configuration). You can prefix the parameters in the properties file with: -
     * camel.component.name.option1=value1 - camel.component.name.option2=value2 - camel.dataformat.name.option1=value1
     * - camel.dataformat.name.option2=value2 - camel.language.name.option1=value1 - camel.language.name.option2=value2
     * Where name is the name of the component, dataformat or language such as seda,direct,jaxb.
     * <p/>
     * The auto configuration also works for any options on components that is a complex type (not standard Java type)
     * and there has been an explicit single bean instance registered to the Camel registry via the
     * {@link org.apache.camel.spi.Registry#bind(String, Object)} method or by using the
     * {@link org.apache.camel.BindToRegistry} annotation style.
     * <p/>
     * This option is default enabled.
     */
    public MainConfigurationProperties withAutoConfigurationEnabled(boolean autoConfigurationEnabled) {
        this.autoConfigurationEnabled = autoConfigurationEnabled;
        return this;
    }

    /**
     * Whether auto configuration should include OS environment variables as well. When enabled this allows to overrule
     * any configuration using an OS environment variable. For example to set a shutdown timeout of 5 seconds:
     * CAMEL_MAIN_SHUTDOWNTIMEOUT=5.
     * <p/>
     * This option is default enabled.
     */
    public MainConfigurationProperties withAutoConfigurationEnvironmentVariablesEnabled(
            boolean autoConfigurationEnvironmentVariablesEnabled) {
        this.autoConfigurationEnvironmentVariablesEnabled = autoConfigurationEnvironmentVariablesEnabled;
        return this;
    }

    /**
     * Whether auto configuration should include JVM system properties as well. When enabled this allows to overrule any
     * configuration using a JVM system property. For example to set a shutdown timeout of 5 seconds: -D
     * camel.main.shutdown-timeout=5.
     * <p/>
     * Note that JVM system properties take precedence over OS environment variables.
     * <p/>
     * This option is default enabled.
     */
    public MainConfigurationProperties withAutoConfigurationSystemPropertiesEnabled(
            boolean autoConfigurationSystemPropertiesEnabled) {
        this.autoConfigurationSystemPropertiesEnabled = autoConfigurationSystemPropertiesEnabled;
        return this;
    }

    /**
     * Whether auto configuration should fail fast when configuring one ore more properties fails for whatever reason
     * such as a invalid property name, etc.
     * <p/>
     * This option is default enabled.
     */
    public MainConfigurationProperties withAutoConfigurationFailFast(boolean autoConfigurationFailFast) {
        this.autoConfigurationFailFast = autoConfigurationFailFast;
        return this;
    }

    /**
     * Whether auto configuration should log a summary with the configured properties.
     * <p/>
     * This option is default enabled.
     */
    public MainConfigurationProperties withAutoConfigurationLogSummary(boolean autoConfigurationLogSummary) {
        this.autoConfigurationLogSummary = autoConfigurationLogSummary;
        return this;
    }

    /**
     * Sets the exit code for the application if duration was hit
     */
    public MainConfigurationProperties withDurationHitExitCode(int durationHitExitCode) {
        this.durationHitExitCode = durationHitExitCode;
        return this;
    }

    /**
     * Extra timeout in seconds to graceful shutdown Camel.
     *
     * When Camel is shutting down then Camel first shutdown all the routes (shutdownTimeout). Then additional services
     * is shutdown (extraShutdownTimeout).
     */
    public MainConfigurationProperties withExtraShutdownTimeout(int extraShutdownTimeout) {
        this.extraShutdownTimeout = extraShutdownTimeout;
        return this;
    }

    /**
     * Package name to use as base (offset) for classpath scanning of {@link RouteBuilder},
     * {@link org.apache.camel.TypeConverter}, {@link CamelConfiguration} classes, and also classes annotated with
     * {@link org.apache.camel.Converter}, or {@link org.apache.camel.BindToRegistry}.
     *
     * If you are using Spring Boot then it is instead recommended to use Spring Boots component scanning and annotate
     * your route builder classes with `@Component`. In other words only use this for Camel Main in standalone mode.
     */
    public MainConfigurationProperties withBasePackageScan(String basePackageScan) {
        this.basePackageScan = basePackageScan;
        return this;
    }

    /**
     * Whether base package scan is enabled.
     */
    public MainConfigurationProperties withBasePackageScanEnabled(boolean basePackageScanEnabled) {
        this.basePackageScanEnabled = basePackageScanEnabled;
        return this;
    }

    // fluent builders - configurations
    // --------------------------------------------------------------

    /**
     * Adds classes names that will be used to configure the camel context as example by providing custom beans through
     * {@link org.apache.camel.BindToRegistry} annotation.
     */
    public MainConfigurationProperties withConfigurations(String configurations) {
        if (this.configurationClasses == null) {
            this.configurationClasses = "";
        }
        if (this.configurationClasses.isEmpty()) {
            this.configurationClasses = configurations;
        } else {
            this.configurationClasses = "," + configurations;
        }
        return this;
    }

    /**
     * Adds a configuration class to the known list of configurations classes.
     */
    @SuppressWarnings("unchecked")
    public MainConfigurationProperties withConfigurations(
            Class<? extends CamelConfiguration>... configuration) {
        addConfigurationClass(configuration);
        return this;
    }

    /**
     * Sets the configuration objects used to configure the camel context.
     */
    public MainConfigurationProperties withConfigurations(List<CamelConfiguration> configurations) {
        setConfigurations(configurations);
        return this;
    }

    // fluent  builder - routes builders
    // --------------------------------------------------------------

    /**
     * Sets classes names that implement {@link RoutesBuilder}.
     */
    public MainConfigurationProperties withRoutesBuilderClasses(String builders) {
        setRoutesBuilderClasses(builders);
        return this;
    }

    /**
     * Sets the RoutesBuilder instances.
     */
    public MainConfigurationProperties withRoutesBuilders(List<RoutesBuilder> builders) {
        setRoutesBuilders(builders);
        return this;
    }

    /**
     * Add an additional {@link RoutesBuilder} object to the known list of builders.
     */
    public MainConfigurationProperties withAdditionalRoutesBuilder(RoutesBuilder builder) {
        addRoutesBuilder(builder);
        return this;
    }

    /**
     * Add an additional {@link RoutesBuilder} class to the known list of builders.
     */
    public MainConfigurationProperties withAdditionalRoutesBuilder(Class... builders) {
        addRoutesBuilder(builders);
        return this;
    }

    /**
     * Add an additional {@link LambdaRouteBuilder} object to the known list of builders.
     */
    public MainConfigurationProperties withAdditionalLambdaRouteBuilder(LambdaRouteBuilder builder) {
        addLambdaRouteBuilder(builder);
        return this;
    }
}
