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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.LambdaRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;

/**
 * Global configuration for Camel Main to setup context name, stream caching and other global configurations.
 */
@Configurer(bootstrap = true)
public class MainConfigurationProperties extends DefaultConfigurationProperties<MainConfigurationProperties>
        implements BootstrapCloseable {

    private boolean autoConfigurationEnabled = true;
    private boolean autoConfigurationEnvironmentVariablesEnabled = true;
    private boolean autoConfigurationFailFast = true;
    private boolean autoConfigurationLogSummary = true;
    private int durationHitExitCode;
    private String packageScanRouteBuilders;

    private String routesBuilderClasses;
    private String configurationClasses;

    private List<RoutesBuilder> routesBuilders = new ArrayList<>();
    private List<Object> configurations = new ArrayList<>();

    // extended configuration
    private HealthConfigurationProperties healthConfigurationProperties;
    private LraConfigurationProperties lraConfigurationProperties;
    private ThreadPoolConfigurationProperties threadPool;
    private HystrixConfigurationProperties hystrixConfigurationProperties;
    private Resilience4jConfigurationProperties resilience4jConfigurationProperties;
    private FaultToleranceConfigurationProperties faultToleranceConfigurationProperties;
    private RestConfigurationProperties restConfigurationProperties;

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
        if (threadPool != null) {
            threadPool.close();
            threadPool = null;
        }
        if (hystrixConfigurationProperties != null) {
            hystrixConfigurationProperties.close();
            hystrixConfigurationProperties = null;
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
        routesBuilders.clear();
        routesBuilders = null;
        configurations.clear();
        configurations = null;
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
     * To configure Saga LRA
     */
    public LraConfigurationProperties lra() {
        if (lraConfigurationProperties == null) {
            lraConfigurationProperties = new LraConfigurationProperties(this);
        }
        return lraConfigurationProperties;
    }

    /**
     * To configure thread pools
     */
    public ThreadPoolConfigurationProperties threadPool() {
        if (threadPool == null) {
            threadPool = new ThreadPoolConfigurationProperties(this);
        }
        return threadPool;
    }

    /**
     * To configure Circuit Breaker EIP with Hystrix
     */
    @Deprecated
    public HystrixConfigurationProperties hystrix() {
        if (hystrixConfigurationProperties == null) {
            hystrixConfigurationProperties = new HystrixConfigurationProperties(this);
        }
        return hystrixConfigurationProperties;
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
     * To configure Circuit Breaker EIP with MicroProfile Fault Tolerance
     */
    public FaultToleranceConfigurationProperties faultTolerance() {
        if (faultToleranceConfigurationProperties == null) {
            faultToleranceConfigurationProperties = new FaultToleranceConfigurationProperties(this);
        }
        return faultToleranceConfigurationProperties;
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

    // getter and setters
    // --------------------------------------------------------------

    public boolean isAutoConfigurationEnabled() {
        return autoConfigurationEnabled;
    }

    /**
     * Whether auto configuration of components, dataformats, languages is enabled or not. When enabled the
     * configuration parameters are loaded from the properties component and optionally from the classpath file
     * META-INF/services/org/apache/camel/autowire.properties. You can prefix the parameters in the properties file
     * with: - camel.component.name.option1=value1 - camel.component.name.option2=value2 -
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

    public String getPackageScanRouteBuilders() {
        return packageScanRouteBuilders;
    }

    /**
     * Sets package names for scanning for {@link org.apache.camel.builder.RouteBuilder} classes as candidates to be
     * included. If you are using Spring Boot then its instead recommended to use Spring Boots component scanning and
     * annotate your route builder classes with `@Component`. In other words only use this for Camel Main in standalone
     * mode.
     */
    public void setPackageScanRouteBuilders(String packageScanRouteBuilders) {
        this.packageScanRouteBuilders = packageScanRouteBuilders;
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
     * Add an additional configuration class to the known list of configurations classes.
     */
    public void addConfigurationClass(Class<?>... configuration) {
        String existing = configurationClasses;
        if (existing == null) {
            existing = "";
        }
        if (configuration != null) {
            for (Class clazz : configuration) {
                if (!existing.isEmpty()) {
                    existing = existing + ",";
                }
                existing = existing + clazz.getName();
            }
        }
        setConfigurationClasses(existing);
    }

    /**
     * Add an additional configuration object to the known list of configurations objects.
     */
    public void addConfiguration(Object configuration) {
        configurations.add(configuration);
    }

    public List<Object> getConfigurations() {
        return configurations;
    }

    /**
     * Sets the configuration objects used to configure the camel context.
     */
    public void setConfigurations(List<Object> configurations) {
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
        String existing = routesBuilderClasses;
        if (existing == null) {
            existing = "";
        }
        if (routeBuilder != null) {
            for (Class<?> clazz : routeBuilder) {
                if (!existing.isEmpty()) {
                    existing = existing + ",";
                }
                existing = existing + clazz.getName();
            }
        }
        setRoutesBuilderClasses(existing);
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
     * Sets package names for scanning for {@link org.apache.camel.builder.RouteBuilder} classes as candidates to be
     * included. If you are using Spring Boot then its instead recommended to use Spring Boots component scanning and
     * annotate your route builder classes with `@Component`. In other words only use this for Camel Main in standalone
     * mode.
     */
    public MainConfigurationProperties withPackageScanRouteBuilders(String packageScanRouteBuilders) {
        this.packageScanRouteBuilders = packageScanRouteBuilders;
        return this;
    }

    // fluent builders - configurations
    // --------------------------------------------------------------

    /**
     * Sets classes names that will be used to configure the camel context as example by providing custom beans through
     * {@link org.apache.camel.BindToRegistry} annotation.
     */
    public MainConfigurationProperties withConfigurationClasses(String configurations) {
        setConfigurationClasses(configurations);
        return this;
    }

    /**
     * Add an additional configuration class to the known list of configurations classes.
     */
    public MainConfigurationProperties withAdditionalConfigurationClasses(Class... configuration) {
        addConfigurationClass(configuration);
        return this;
    }

    /**
     * Add an additional configuration object to the known list of configurations objects.
     */
    public MainConfigurationProperties withAdditionalConfiguration(Object configuration) {
        addConfiguration(configuration);
        return this;
    }

    /**
     * Sets the configuration objects used to configure the camel context.
     */
    public MainConfigurationProperties withConfigurations(List<Object> configurations) {
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
