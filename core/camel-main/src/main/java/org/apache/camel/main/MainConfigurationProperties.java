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

/**
 * Global configuration for Camel Main to setup context name, stream caching and other global configurations.
 */
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
    private boolean hangupInterceptorEnabled = true;
    private String packageScanRouteBuilders;

    // extended configuration
    private final HystrixConfigurationProperties hystrixConfigurationProperties = new HystrixConfigurationProperties(this);
    private final Resilience4jConfigurationProperties resilience4jConfigurationProperties = new Resilience4jConfigurationProperties(this);
    private final RestConfigurationProperties restConfigurationProperties = new RestConfigurationProperties(this);

    // extended
    // --------------------------------------------------------------

    /**
     * To configure Circuit Breaker EIP with Hystrix
     */
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

    public boolean isHangupInterceptorEnabled() {
        return hangupInterceptorEnabled;
    }

    /**
     * Whether to use graceful hangup when Camel is stopping or when the JVM terminates.
     */
    public void setHangupInterceptorEnabled(boolean hangupInterceptorEnabled) {
        this.hangupInterceptorEnabled = hangupInterceptorEnabled;
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
     * Whether to use graceful hangup when Camel is stopping or when the JVM terminates.
     */
    public MainConfigurationProperties withHangupInterceptorEnabled(boolean hangupInterceptorEnabled) {
        this.hangupInterceptorEnabled = hangupInterceptorEnabled;
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

}
