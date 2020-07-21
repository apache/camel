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
import org.apache.camel.spi.Configurer;

/**
 * Global configuration for Camel Main to setup context name, stream caching and other global configurations.
 */
@Configurer
public class MainConfigurationProperties extends DefaultConfigurationProperties<MainConfigurationProperties> {

    private boolean autoConfigurationEnabled = true;
    private boolean autoConfigurationEnvironmentVariablesEnabled = true;
    private boolean autoConfigurationFailFast = true;
    private boolean autoConfigurationLogSummary = true;
    private boolean autowireComponentProperties = true;
    private boolean autowireComponentPropertiesDeep;
    private boolean autowireComponentPropertiesNonNullOnly;
    private boolean autowireComponentPropertiesAllowPrivateSetter = true;
    private int durationHitExitCode;
    private String packageScanRouteBuilders;

    private String routesBuilderClasses;
    private String configurationClasses;

    private List<RoutesBuilder> routesBuilders = new ArrayList<>();
    private List<Object> configurations = new ArrayList<>();

    // extended configuration
    private final HealthConfigurationProperties healthConfigurationProperties = new HealthConfigurationProperties(this);
    private final LraConfigurationProperties lraConfigurationProperties = new LraConfigurationProperties(this);
    private final ThreadPoolConfigurationProperties threadPool = new ThreadPoolConfigurationProperties(this);
    private final HystrixConfigurationProperties hystrixConfigurationProperties = new HystrixConfigurationProperties(this);
    private final Resilience4jConfigurationProperties resilience4jConfigurationProperties = new Resilience4jConfigurationProperties(this);
    private final FaultToleranceConfigurationProperties faultToleranceConfigurationProperties = new FaultToleranceConfigurationProperties(this);
    private final RestConfigurationProperties restConfigurationProperties = new RestConfigurationProperties(this);

    // extended
    // --------------------------------------------------------------

    /**
     * To configure Health Check
     */
    public HealthConfigurationProperties health() {
        return healthConfigurationProperties;
    }

    /**
     * To configure Saga LRA
     */
    public LraConfigurationProperties lra() {
        return lraConfigurationProperties;
    }

    /**
     * To configure thread pools
     */
    public ThreadPoolConfigurationProperties threadPool() {
        return threadPool;
    }

    /**
     * To configure Circuit Breaker EIP with Hystrix
     */
    @Deprecated
    public HystrixConfigurationProperties hystrix() {
        return hystrixConfigurationProperties;
    }

    /**
     * To configure Circuit Breaker EIP with Resilience4j
     */
    public Resilience4jConfigurationProperties resilience4j() {
        return resilience4jConfigurationProperties;
    }

    /**
     * To configure Circuit Breaker EIP with MicroProfile Fault Tolerance
     */
    public FaultToleranceConfigurationProperties faultTolerance() {
        return faultToleranceConfigurationProperties;
    }

    /**
     * To configure Rest DSL
     */
    public RestConfigurationProperties rest() {
        return restConfigurationProperties;
    }

    // getter and setters
    // --------------------------------------------------------------

    public boolean isAutoConfigurationEnabled() {
        return autoConfigurationEnabled;
    }

    /**
     * Whether auto configuration of components, dataformats, languages is enabled or not.
     * When enabled the configuration parameters are loaded from the properties component
     * and optionally from the classpath file META-INF/services/org/apache/camel/autowire.properties.
     * You can prefix the parameters in the properties file with:
     * - camel.component.name.option1=value1
     * - camel.component.name.option2=value2
     * - camel.dataformat.name.option1=value1
     * - camel.dataformat.name.option2=value2
     * - camel.language.name.option1=value1
     * - camel.language.name.option2=value2
     * Where name is the name of the component, dataformat or language such as seda,direct,jaxb.
     * <p/>
     * The auto configuration also works for any options on components
     * that is a complex type (not standard Java type) and there has been an explicit single
     * bean instance registered to the Camel registry via the {@link org.apache.camel.spi.Registry#bind(String, Object)} method
     * or by using the {@link org.apache.camel.BindToRegistry} annotation style.
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
     * Whether auto configuration should include OS environment variables as well. When enabled this
     * allows to overrule any configuration using an OS environment variable. For example to set
     * a shutdown timeout of 5 seconds: CAMEL_MAIN_SHUTDOWNTIMEOUT=5.
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

    public boolean isAutowireComponentProperties() {
        return autowireComponentProperties;
    }

    /**
     * Whether autowiring components with properties that are of same type, which has been added to the Camel registry, as a singleton instance.
     * This is used for convention over configuration to inject DataSource, AmazonLogin instances to the components.
     * <p/>
     * This option is default enabled.
     */
    public void setAutowireComponentProperties(boolean autowireComponentProperties) {
        this.autowireComponentProperties = autowireComponentProperties;
    }

    public boolean isAutowireComponentPropertiesDeep() {
        return autowireComponentPropertiesDeep;
    }

    /**
     * Whether autowiring components (with deep nesting by attempting to walk as deep down the object graph by creating new empty objects on the way if needed)
     * with properties that are of same type, which has been added to the Camel registry, as a singleton instance.
     * This is used for convention over configuration to inject DataSource, AmazonLogin instances to the components.
     * <p/>
     * This option is default disabled.
     */
    public void setAutowireComponentPropertiesDeep(boolean autowireComponentPropertiesDeep) {
        this.autowireComponentPropertiesDeep = autowireComponentPropertiesDeep;
    }

    public boolean isAutowireComponentPropertiesNonNullOnly() {
        return autowireComponentPropertiesNonNullOnly;
    }

    /**
     * Whether to only autowire if the property has no default value or has not been configured explicit.
     * <p/>
     * This option is default disabled.
     */
    public void setAutowireComponentPropertiesNonNullOnly(boolean autowireComponentPropertiesNonNullOnly) {
        this.autowireComponentPropertiesNonNullOnly = autowireComponentPropertiesNonNullOnly;
    }

    public boolean isAutowireComponentPropertiesAllowPrivateSetter() {
        return autowireComponentPropertiesAllowPrivateSetter;
    }

    /**
     * Whether autowiring components allows to use private setter method when setting the value. This may be needed
     * in some rare situations when some configuration classes may configure via constructors over setters. But
     * constructor configuration is more cumbersome to use via .properties files etc.
     */
    public void setAutowireComponentPropertiesAllowPrivateSetter(boolean autowireComponentPropertiesAllowPrivateSetter) {
        this.autowireComponentPropertiesAllowPrivateSetter = autowireComponentPropertiesAllowPrivateSetter;
    }

    public String getPackageScanRouteBuilders() {
        return packageScanRouteBuilders;
    }

    /**
     * Sets package names for scanning for {@link org.apache.camel.builder.RouteBuilder} classes as candidates to be included.
     * If you are using Spring Boot then its instead recommended to use Spring Boots component scanning and annotate your route builder
     * classes with `@Component`. In other words only use this for Camel Main in standalone mode.
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
     * Sets classes names that will be used to configure the camel context as example by providing custom beans
     * through {@link org.apache.camel.BindToRegistry} annotation.
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
            for (Class clazz : routeBuilder) {
                if (!existing.isEmpty()) {
                    existing = existing + ",";
                }
                existing = existing + clazz.getName();
            }
        }
        setRoutesBuilderClasses(existing);
    }

    // fluent builders
    // --------------------------------------------------------------

    /**
     * Whether auto configuration of components/dataformats/languages is enabled or not.
     * When enabled the configuration parameters are loaded from the properties component
     * and configured as defaults (similar to spring-boot auto-configuration). You can prefix
     * the parameters in the properties file with:
     * - camel.component.name.option1=value1
     * - camel.component.name.option2=value2
     * - camel.dataformat.name.option1=value1
     * - camel.dataformat.name.option2=value2
     * - camel.language.name.option1=value1
     * - camel.language.name.option2=value2
     * Where name is the name of the component, dataformat or language such as seda,direct,jaxb.
     * <p/>
     * The auto configuration also works for any options on components
     * that is a complex type (not standard Java type) and there has been an explicit single
     * bean instance registered to the Camel registry via the {@link org.apache.camel.spi.Registry#bind(String, Object)} method
     * or by using the {@link org.apache.camel.BindToRegistry} annotation style.
     * <p/>
     * This option is default enabled.
     */
    public MainConfigurationProperties withAutoConfigurationEnabled(boolean autoConfigurationEnabled) {
        this.autoConfigurationEnabled = autoConfigurationEnabled;
        return this;
    }

    /**
     * Whether auto configuration should include OS environment variables as well. When enabled this
     * allows to overrule any configuration using an OS environment variable. For example to set
     * a shutdown timeout of 5 seconds: CAMEL_MAIN_SHUTDOWNTIMEOUT=5.
     * <p/>
     * This option is default enabled.
     */
    public MainConfigurationProperties withAutoConfigurationEnvironmentVariablesEnabled(boolean autoConfigurationEnvironmentVariablesEnabled) {
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
     * Whether autowiring components with properties that are of same type, which has been added to the Camel registry, as a singleton instance.
     * This is used for convention over configuration to inject DataSource, AmazonLogin instances to the components.
     * <p/>
     * This option is default enabled.
     */
    public MainConfigurationProperties withAutowireComponentProperties(boolean autowireComponentProperties) {
        this.autowireComponentProperties = autowireComponentProperties;
        return this;
    }

    /**
     * Whether autowiring components (with deep nesting by attempting to walk as deep down the object graph by creating new empty objects on the way if needed)
     * with properties that are of same type, which has been added to the Camel registry, as a singleton instance.
     * This is used for convention over configuration to inject DataSource, AmazonLogin instances to the components.
     * <p/>
     * This option is default disabled.
     */
    public MainConfigurationProperties withAutowireComponentPropertiesDeep(boolean autowireComponentPropertiesDeep) {
        this.autowireComponentPropertiesDeep = autowireComponentPropertiesDeep;
        return this;
    }

    /**
     * Whether to only autowire if the property has no default value or has not been configured explicit.
     * <p/>
     * This option is default disabled.
     */
    public MainConfigurationProperties withAutowireComponentPropertiesNonNullOnly(boolean autowireComponentPropertiesNonNullOnly) {
        this.autowireComponentPropertiesNonNullOnly = autowireComponentPropertiesNonNullOnly;
        return this;
    }

    /**
     * Whether autowiring components (with deep nesting by attempting to walk as deep down the object graph by creating new empty objects on the way if needed)
     * with properties that are of same type, which has been added to the Camel registry, as a singleton instance.
     * This is used for convention over configuration to inject DataSource, AmazonLogin instances to the components.
     * <p/>
     * This option is default enabled.
     */
    public MainConfigurationProperties withAutowireComponentPropertiesAllowPrivateSetter(boolean autowireComponentPropertiesAllowPrivateSetter) {
        this.autowireComponentPropertiesAllowPrivateSetter = autowireComponentPropertiesAllowPrivateSetter;
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
     * Sets package names for scanning for {@link org.apache.camel.builder.RouteBuilder} classes as candidates to be included.
     * If you are using Spring Boot then its instead recommended to use Spring Boots component scanning and annotate your route builder
     * classes with `@Component`. In other words only use this for Camel Main in standalone mode.
     */
    public MainConfigurationProperties withPackageScanRouteBuilders(String packageScanRouteBuilders) {
        this.packageScanRouteBuilders = packageScanRouteBuilders;
        return this;
    }

    // fluent builders - configurations
    // --------------------------------------------------------------

    /**
     * Sets classes names that will be used to configure the camel context as example by providing custom beans
     * through {@link org.apache.camel.BindToRegistry} annotation.
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
}
