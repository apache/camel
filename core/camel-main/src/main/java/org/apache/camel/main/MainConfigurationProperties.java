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
    private boolean autowireComponentProperties = true;
    private boolean autowireComponentPropertiesDeep;
    private boolean autowireComponentPropertiesAllowPrivateSetter = true;
    private long duration = -1;
    private int durationHitExitCode;
    private boolean hangupInterceptorEnabled = true;

    // extended configuration
    private final HystrixConfigurationProperties hystrixConfigurationProperties = new HystrixConfigurationProperties(this);
    private final RestConfigurationProperties restConfigurationProperties = new RestConfigurationProperties(this);

    // extended
    // --------------------------------------------------------------

    /**
     * To configure Hystrix EIP
     */
    public HystrixConfigurationProperties hystrix() {
        return hystrixConfigurationProperties;
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
     * Whether auto configuration of components/dataformats/languages is enabled or not.
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

    public long getDuration() {
        return duration;
    }

    /**
     * Sets the duration (in seconds) to run the application until it
     * should be terminated. Defaults to -1. Any value <= 0 will run forever.
     */
    public void setDuration(long duration) {
        this.duration = duration;
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
     * Sets the duration (in seconds) to run the application until it
     * should be terminated. Defaults to -1. Any value <= 0 will run forever.
     */
    public MainConfigurationProperties withDuration(long duration) {
        this.duration = duration;
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

}
