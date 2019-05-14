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

import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.spi.ReloadStrategy;

/**
 * Global configuration for Camel Main to setup context name, stream caching and other global configurations.
 */
public class MainConfigurationProperties {

    private boolean autoConfigurationEnabled = true;
    private String name;
    private int shutdownTimeout = 300;
    private boolean shutdownSuppressLoggingOnTimeout;
    private boolean shutdownNowOnTimeout = true;
    private boolean shutdownRoutesInReverseOrder = true;
    private boolean shutdownLogInflightExchangesOnTimeout = true;
    private boolean jmxEnabled = true;
    private int producerTemplateCacheSize = 1000;
    private int consumerTemplateCacheSize = 1000;
    private String fileConfigurations;
    private long duration = -1;
    private int durationMaxSeconds;
    private int durationMaxIdleSeconds;
    private int durationMaxMessages;
    private boolean hangupInterceptorEnabled = true;
    private int durationHitExitCode;
    private int logDebugMaxChars;
    private boolean streamCachingEnabled;
    private String streamCachingSpoolDirectory;
    private String streamCachingSpoolChiper;
    private long streamCachingSpoolThreshold;
    private int streamCachingSpoolUsedHeapMemoryThreshold;
    private String streamCachingSpoolUsedHeapMemoryLimit;
    private boolean streamCachingAnySpoolRules;
    private int streamCachingBufferSize;
    private boolean streamCachingRemoveSpoolDirectoryWhenStopping = true;
    private boolean streamCachingStatisticsEnabled;
    private boolean tracing;
    private boolean messageHistory = true;
    private boolean logMask;
    private boolean logExhaustedMessageBody;
    private boolean handleFault;
    private boolean autoStartup = true;
    private boolean allowUseOriginalMessage;
    private boolean endpointRuntimeStatisticsEnabled;
    private boolean useDataType;
    private boolean useBreadcrumb;
    private ManagementStatisticsLevel jmxManagementStatisticsLevel = ManagementStatisticsLevel.Default;
    private String jmxManagementNamePattern = "#name#";
    private boolean jmxCreateConnector;
    private boolean useMdcLogging;
    private String threadNamePattern;
    private String fileWatchDirectory;
    private boolean fileWatchDirectoryRecursively;
    private ReloadStrategy reloadStrategy;

    // getter and setters
    // --------------------------------------------------------------

    public boolean isAutoConfigurationEnabled() {
        return autoConfigurationEnabled;
    }

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
    public void setAutoConfigurationEnabled(boolean autoConfigurationEnabled) {
        this.autoConfigurationEnabled = autoConfigurationEnabled;
    }

    public String getName() {
        return name;
    }

    /**
     * Sets the name of the CamelContext.
     */
    public void setName(String name) {
        this.name = name;
    }

    public int getShutdownTimeout() {
        return shutdownTimeout;
    }

    /**
     * Timeout in seconds to graceful shutdown Camel.
     */
    public void setShutdownTimeout(int shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    public boolean isShutdownSuppressLoggingOnTimeout() {
        return shutdownSuppressLoggingOnTimeout;
    }

    /**
     * Whether Camel should try to suppress logging during shutdown and timeout was triggered,
     * meaning forced shutdown is happening. And during forced shutdown we want to avoid logging
     * errors/warnings et all in the logs as a side-effect of the forced timeout.
     * Notice the suppress is a best effort as there may still be some logs coming
     * from 3rd party libraries and whatnot, which Camel cannot control.
     * This option is default false.
     */
    public void setShutdownSuppressLoggingOnTimeout(boolean shutdownSuppressLoggingOnTimeout) {
        this.shutdownSuppressLoggingOnTimeout = shutdownSuppressLoggingOnTimeout;
    }

    public boolean isShutdownNowOnTimeout() {
        return shutdownNowOnTimeout;
    }

    /**
     * Sets whether to force shutdown of all consumers when a timeout occurred and thus
     * not all consumers was shutdown within that period.
     *
     * You should have good reasons to set this option to false as it means that the routes
     * keep running and is halted abruptly when CamelContext has been shutdown.
     */
    public void setShutdownNowOnTimeout(boolean shutdownNowOnTimeout) {
        this.shutdownNowOnTimeout = shutdownNowOnTimeout;
    }

    public boolean isShutdownRoutesInReverseOrder() {
        return shutdownRoutesInReverseOrder;
    }

    /**
     * Sets whether routes should be shutdown in reverse or the same order as they where started.
     */
    public void setShutdownRoutesInReverseOrder(boolean shutdownRoutesInReverseOrder) {
        this.shutdownRoutesInReverseOrder = shutdownRoutesInReverseOrder;
    }

    public boolean isShutdownLogInflightExchangesOnTimeout() {
        return shutdownLogInflightExchangesOnTimeout;
    }

    /**
     * Sets whether to log information about the inflight Exchanges which are still running
     * during a shutdown which didn't complete without the given timeout.
     */
    public void setShutdownLogInflightExchangesOnTimeout(boolean shutdownLogInflightExchangesOnTimeout) {
        this.shutdownLogInflightExchangesOnTimeout = shutdownLogInflightExchangesOnTimeout;
    }

    public boolean isJmxEnabled() {
        return jmxEnabled;
    }

    /**
     * Enable JMX in your Camel application.
     */
    public void setJmxEnabled(boolean jmxEnabled) {
        this.jmxEnabled = jmxEnabled;
    }

    public int getProducerTemplateCacheSize() {
        return producerTemplateCacheSize;
    }

    /**
     * Producer template endpoints cache size.
     */
    public void setProducerTemplateCacheSize(int producerTemplateCacheSize) {
        this.producerTemplateCacheSize = producerTemplateCacheSize;
    }

    public int getConsumerTemplateCacheSize() {
        return consumerTemplateCacheSize;
    }

    /**
     * Consumer template endpoints cache size.
     */
    public void setConsumerTemplateCacheSize(int consumerTemplateCacheSize) {
        this.consumerTemplateCacheSize = consumerTemplateCacheSize;
    }

    public String getFileConfigurations() {
        return fileConfigurations;
    }

    /**
     * Directory to load additional configuration files that contains
     * configuration values that takes precedence over any other configuration.
     * This can be used to refer to files that may have secret configuration that
     * has been mounted on the file system for containers.
     *
     * You can specify a pattern to load from sub directories and a name pattern such as /var/app/secret/*.properties,
     * multiple directories can be separated by comma.
     */
    public void setFileConfigurations(String fileConfigurations) {
        this.fileConfigurations = fileConfigurations;
    }

    public int getDurationMaxSeconds() {
        return durationMaxSeconds;
    }

    /**
     * To specify for how long time in seconds to keep running the JVM before automatic terminating the JVM.
     * You can use this to run Camel for a short while.
     */
    public void setDurationMaxSeconds(int durationMaxSeconds) {
        this.durationMaxSeconds = durationMaxSeconds;
    }

    public int getDurationMaxIdleSeconds() {
        return durationMaxIdleSeconds;
    }

    /**
     * To specify for how long time in seconds Camel can be idle before automatic terminating the JVM.
     * You can use this to run Camel for a short while.
     */
    public void setDurationMaxIdleSeconds(int durationMaxIdleSeconds) {
        this.durationMaxIdleSeconds = durationMaxIdleSeconds;
    }

    public int getDurationMaxMessages() {
        return durationMaxMessages;
    }

    /**
     * To specify how many messages to process by Camel before automatic terminating the JVM.
     * You can use this to run Camel for a short while.
     */
    public void setDurationMaxMessages(int durationMaxMessages) {
        this.durationMaxMessages = durationMaxMessages;
    }

    public int getLogDebugMaxChars() {
        return logDebugMaxChars;
    }

    /**
     * Is used to limit the maximum length of the logging Camel message bodies. If the message body
     * is longer than the limit, the log message is clipped. Use -1 to have unlimited length.
     * Use for example 1000 to log at most 1000 characters.
     */
    public void setLogDebugMaxChars(int logDebugMaxChars) {
        this.logDebugMaxChars = logDebugMaxChars;
    }

    public boolean isStreamCachingEnabled() {
        return streamCachingEnabled;
    }

    /**
     * Sets whether stream caching is enabled or not.
     *
     * Default is false.
     */
    public void setStreamCachingEnabled(boolean streamCachingEnabled) {
        this.streamCachingEnabled = streamCachingEnabled;
    }

    public String getStreamCachingSpoolDirectory() {
        return streamCachingSpoolDirectory;
    }

    /**
     * Sets the stream caching spool (temporary) directory to use for overflow and spooling to disk.
     *
     * If no spool directory has been explicit configured, then a temporary directory
     * is created in the java.io.tmpdir directory.
     */
    public void setStreamCachingSpoolDirectory(String streamCachingSpoolDirectory) {
        this.streamCachingSpoolDirectory = streamCachingSpoolDirectory;
    }

    public String getStreamCachingSpoolChiper() {
        return streamCachingSpoolChiper;
    }

    /**
     * Sets a stream caching chiper name to use when spooling to disk to write with encryption.
     * By default the data is not encrypted.
     */
    public void setStreamCachingSpoolChiper(String streamCachingSpoolChiper) {
        this.streamCachingSpoolChiper = streamCachingSpoolChiper;
    }

    public long getStreamCachingSpoolThreshold() {
        return streamCachingSpoolThreshold;
    }

    /**
     * Stream caching threshold in bytes when overflow to disk is activated.
     * The default threshold is 128kb.
     * Use -1 to disable overflow to disk.
     */
    public void setStreamCachingSpoolThreshold(long streamCachingSpoolThreshold) {
        this.streamCachingSpoolThreshold = streamCachingSpoolThreshold;
    }

    public int getStreamCachingSpoolUsedHeapMemoryThreshold() {
        return streamCachingSpoolUsedHeapMemoryThreshold;
    }

    /**
     * Sets a percentage (1-99) of used heap memory threshold to activate stream caching spooling to disk.
     */
    public void setStreamCachingSpoolUsedHeapMemoryThreshold(int streamCachingSpoolUsedHeapMemoryThreshold) {
        this.streamCachingSpoolUsedHeapMemoryThreshold = streamCachingSpoolUsedHeapMemoryThreshold;
    }

    public String getStreamCachingSpoolUsedHeapMemoryLimit() {
        return streamCachingSpoolUsedHeapMemoryLimit;
    }

    /**
     * Sets what the upper bounds should be when streamCachingSpoolUsedHeapMemoryThreshold is in use.
     */
    public void setStreamCachingSpoolUsedHeapMemoryLimit(String streamCachingSpoolUsedHeapMemoryLimit) {
        this.streamCachingSpoolUsedHeapMemoryLimit = streamCachingSpoolUsedHeapMemoryLimit;
    }

    public boolean isStreamCachingAnySpoolRules() {
        return streamCachingAnySpoolRules;
    }

    /**
     * Sets whether if just any of the org.apache.camel.spi.StreamCachingStrategy.SpoolRule rules
     * returns true then shouldSpoolCache(long) returns true, to allow spooling to disk.
     * If this option is false, then all the org.apache.camel.spi.StreamCachingStrategy.SpoolRule must
     * return true.
     *
     * The default value is false which means that all the rules must return true.
     */
    public void setStreamCachingAnySpoolRules(boolean streamCachingAnySpoolRules) {
        this.streamCachingAnySpoolRules = streamCachingAnySpoolRules;
    }

    public int getStreamCachingBufferSize() {
        return streamCachingBufferSize;
    }

    /**
     * Sets the stream caching buffer size to use when allocating in-memory buffers used for in-memory stream caches.
     *
     * The default size is 4096.
     */
    public void setStreamCachingBufferSize(int streamCachingBufferSize) {
        this.streamCachingBufferSize = streamCachingBufferSize;
    }

    public boolean isStreamCachingRemoveSpoolDirectoryWhenStopping() {
        return streamCachingRemoveSpoolDirectoryWhenStopping;
    }

    /**
     * Whether to remove stream caching temporary directory when stopping.
     * This option is default true.
     */
    public void setStreamCachingRemoveSpoolDirectoryWhenStopping(boolean streamCachingRemoveSpoolDirectoryWhenStopping) {
        this.streamCachingRemoveSpoolDirectoryWhenStopping = streamCachingRemoveSpoolDirectoryWhenStopping;
    }

    public boolean isStreamCachingStatisticsEnabled() {
        return streamCachingStatisticsEnabled;
    }

    /**
     * Sets whether stream caching statistics is enabled.
     */
    public void setStreamCachingStatisticsEnabled(boolean streamCachingStatisticsEnabled) {
        this.streamCachingStatisticsEnabled = streamCachingStatisticsEnabled;
    }

    public boolean isTracing() {
        return tracing;
    }

    /**
     * Sets whether tracing is enabled or not.
     *
     * Default is false.
     */
    public void setTracing(boolean tracing) {
        this.tracing = tracing;
    }

    public boolean isMessageHistory() {
        return messageHistory;
    }

    /**
     * Sets whether message history is enabled or not.
     *
     * Default is true.
     */
    public void setMessageHistory(boolean messageHistory) {
        this.messageHistory = messageHistory;
    }

    public boolean isLogMask() {
        return logMask;
    }

    /**
     * Sets whether log mask is enabled or not.
     *
     * Default is false.
     */
    public void setLogMask(boolean logMask) {
        this.logMask = logMask;
    }

    public boolean isLogExhaustedMessageBody() {
        return logExhaustedMessageBody;
    }

    /**
     * Sets whether to log exhausted message body with message history.
     *
     * Default is false.
     */
    public void setLogExhaustedMessageBody(boolean logExhaustedMessageBody) {
        this.logExhaustedMessageBody = logExhaustedMessageBody;
    }

    public boolean isHandleFault() {
        return handleFault;
    }

    /**
     * Sets whether fault handling is enabled or not.
     *
     * Default is false.
     */
    public void setHandleFault(boolean handleFault) {
        this.handleFault = handleFault;
    }

    public boolean isAutoStartup() {
        return autoStartup;
    }

    /**
     * Sets whether the object should automatically start when Camel starts.
     * Important: Currently only routes can be disabled, as CamelContext's are always started.
     * Note: When setting auto startup false on CamelContext then that takes precedence
     * and no routes is started. You would need to start CamelContext explicit using
     * the org.apache.camel.CamelContext.start() method, to start the context, and then
     * you would need to start the routes manually using CamelContext.getRouteController().startRoute(String).
     *
     * Default is true to always start up.
     */
    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public boolean isAllowUseOriginalMessage() {
        return allowUseOriginalMessage;
    }

    /**
     * Sets whether to allow access to the original message from Camel's error handler,
     * or from org.apache.camel.spi.UnitOfWork.getOriginalInMessage().
     * Turning this off can optimize performance, as defensive copy of the original message is not needed.
     *
     * Default is false.
     */
    public void setAllowUseOriginalMessage(boolean allowUseOriginalMessage) {
        this.allowUseOriginalMessage = allowUseOriginalMessage;
    }

    public boolean isEndpointRuntimeStatisticsEnabled() {
        return endpointRuntimeStatisticsEnabled;
    }

    /**
     * Sets whether endpoint runtime statistics is enabled (gathers runtime usage of each incoming and outgoing endpoints).
     *
     * The default value is false.
     */
    public void setEndpointRuntimeStatisticsEnabled(boolean endpointRuntimeStatisticsEnabled) {
        this.endpointRuntimeStatisticsEnabled = endpointRuntimeStatisticsEnabled;
    }

    public boolean isUseDataType() {
        return useDataType;
    }

    /**
     * Whether to enable using data type on Camel messages.
     *
     * Data type are automatic turned on if one ore more routes has been explicit configured with input and output types.
     * Otherwise data type is default off.
     */
    public void setUseDataType(boolean useDataType) {
        this.useDataType = useDataType;
    }

    public boolean isUseBreadcrumb() {
        return useBreadcrumb;
    }

    /**
     * Set whether breadcrumb is enabled.
     * The default value is false.
     */
    public void setUseBreadcrumb(boolean useBreadcrumb) {
        this.useBreadcrumb = useBreadcrumb;
    }

    public ManagementStatisticsLevel getJmxManagementStatisticsLevel() {
        return jmxManagementStatisticsLevel;
    }

    /**
     * Sets the JMX statistics level
     * The level can be set to Extended to gather additional information
     *
     * The default value is Default.
     */
    public void setJmxManagementStatisticsLevel(ManagementStatisticsLevel jmxManagementStatisticsLevel) {
        this.jmxManagementStatisticsLevel = jmxManagementStatisticsLevel;
    }

    public String getJmxManagementNamePattern() {
        return jmxManagementNamePattern;
    }

    /**
     * The naming pattern for creating the CamelContext JMX management name.
     *
     * The default pattern is #name#
     */
    public void setJmxManagementNamePattern(String jmxManagementNamePattern) {
        this.jmxManagementNamePattern = jmxManagementNamePattern;
    }

    public boolean isJmxCreateConnector() {
        return jmxCreateConnector;
    }

    /**
     * Whether JMX connector is created, allowing clients to connect remotely
     *
     * The default value is false.
     */
    public void setJmxCreateConnector(boolean jmxCreateConnector) {
        this.jmxCreateConnector = jmxCreateConnector;
    }

    public boolean isUseMdcLogging() {
        return useMdcLogging;
    }

    /**
     * To turn on MDC logging
     */
    public void setUseMdcLogging(boolean useMdcLogging) {
        this.useMdcLogging = useMdcLogging;
    }

    public String getThreadNamePattern() {
        return threadNamePattern;
    }

    /**
     * Sets the thread name pattern used for creating the full thread name.
     *
     * The default pattern is: Camel (#camelId#) thread ##counter# - #name#
     *
     * Where #camelId# is the name of the CamelContext.
     * and #counter# is a unique incrementing counter.
     * and #name# is the regular thread name.
     *
     * You can also use #longName# which is the long thread name which can includes endpoint parameters etc.
     */
    public void setThreadNamePattern(String threadNamePattern) {
        this.threadNamePattern = threadNamePattern;
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

    public String getFileWatchDirectory() {
        return fileWatchDirectory;
    }

    /**
     * Sets the directory name to watch XML file changes to trigger live reload of Camel routes.
     * <p/>
     * Notice you cannot set this value and a custom {@link ReloadStrategy} as well.
     */
    public void setFileWatchDirectory(String fileWatchDirectory) {
        this.fileWatchDirectory = fileWatchDirectory;
    }

    public boolean isFileWatchDirectoryRecursively() {
        return fileWatchDirectoryRecursively;
    }

    /**
     * Sets the flag to watch directory of XML file changes recursively to trigger live reload of Camel routes.
     * <p/>
     * Notice you cannot set this value and a custom {@link ReloadStrategy} as well.
     */
    public void setFileWatchDirectoryRecursively(boolean fileWatchDirectoryRecursively) {
        this.fileWatchDirectoryRecursively = fileWatchDirectoryRecursively;
    }

    public ReloadStrategy getReloadStrategy() {
        return reloadStrategy;
    }

    /**
     * Sets a custom {@link ReloadStrategy} to be used.
     * <p/>
     * Notice you cannot set this value and the fileWatchDirectory as well.
     */
    public void setReloadStrategy(ReloadStrategy reloadStrategy) {
        this.reloadStrategy = reloadStrategy;
    }

    // fluent builders
    // --------------------------------------------------------------

    public MainConfigurationProperties withAutoConfigurationEnabled(boolean autoConfigurationEnabled) {
        this.autoConfigurationEnabled = autoConfigurationEnabled;
        return this;
    }

    public MainConfigurationProperties withName(String name) {
        this.name = name;
        return this;
    }

    public MainConfigurationProperties withShutdownTimeout(int shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
        return this;
    }

    public MainConfigurationProperties withShutdownSuppressLoggingOnTimeout(boolean shutdownSuppressLoggingOnTimeout) {
        this.shutdownSuppressLoggingOnTimeout = shutdownSuppressLoggingOnTimeout;
        return this;
    }

    public MainConfigurationProperties withShutdownNowOnTimeout(boolean shutdownNowOnTimeout) {
        this.shutdownNowOnTimeout = shutdownNowOnTimeout;
        return this;
    }

    public MainConfigurationProperties withShutdownRoutesInReverseOrder(boolean shutdownRoutesInReverseOrder) {
        this.shutdownRoutesInReverseOrder = shutdownRoutesInReverseOrder;
        return this;
    }

    public MainConfigurationProperties withShutdownLogInflightExchangesOnTimeout(boolean shutdownLogInflightExchangesOnTimeout) {
        this.shutdownLogInflightExchangesOnTimeout = shutdownLogInflightExchangesOnTimeout;
        return this;
    }

    public MainConfigurationProperties withJmxEnabled(boolean jmxEnabled) {
        this.jmxEnabled = jmxEnabled;
        return this;
    }

    public MainConfigurationProperties withProducerTemplateCacheSize(int producerTemplateCacheSize) {
        this.producerTemplateCacheSize = producerTemplateCacheSize;
        return this;
    }

    public MainConfigurationProperties withConsumerTemplateCacheSize(int consumerTemplateCacheSize) {
        this.consumerTemplateCacheSize = consumerTemplateCacheSize;
        return this;
    }

    public MainConfigurationProperties withFileConfigurations(String fileConfigurations) {
        this.fileConfigurations = fileConfigurations;
        return this;
    }

    public MainConfigurationProperties withDurationMaxSeconds(int durationMaxSeconds) {
        this.durationMaxSeconds = durationMaxSeconds;
        return this;
    }

    public MainConfigurationProperties withDurationMaxIdleSeconds(int durationMaxIdleSeconds) {
        this.durationMaxIdleSeconds = durationMaxIdleSeconds;
        return this;
    }

    public MainConfigurationProperties withDurationMaxMessages(int durationMaxMessages) {
        this.durationMaxMessages = durationMaxMessages;
        return this;
    }

    public MainConfigurationProperties withLogDebugMaxChars(int logDebugMaxChars) {
        this.logDebugMaxChars = logDebugMaxChars;
        return this;
    }

    public MainConfigurationProperties withStreamCachingEnabled(boolean streamCachingEnabled) {
        this.streamCachingEnabled = streamCachingEnabled;
        return this;
    }

    public MainConfigurationProperties withStreamCachingSpoolDirectory(String streamCachingSpoolDirectory) {
        this.streamCachingSpoolDirectory = streamCachingSpoolDirectory;
        return this;
    }

    public MainConfigurationProperties withStreamCachingSpoolChiper(String streamCachingSpoolChiper) {
        this.streamCachingSpoolChiper = streamCachingSpoolChiper;
        return this;
    }

    public MainConfigurationProperties withStreamCachingSpoolThreshold(long streamCachingSpoolThreshold) {
        this.streamCachingSpoolThreshold = streamCachingSpoolThreshold;
        return this;
    }

    public MainConfigurationProperties withStreamCachingSpoolUsedHeapMemoryThreshold(int streamCachingSpoolUsedHeapMemoryThreshold) {
        this.streamCachingSpoolUsedHeapMemoryThreshold = streamCachingSpoolUsedHeapMemoryThreshold;
        return this;
    }

    public MainConfigurationProperties withStreamCachingSpoolUsedHeapMemoryLimit(String streamCachingSpoolUsedHeapMemoryLimit) {
        this.streamCachingSpoolUsedHeapMemoryLimit = streamCachingSpoolUsedHeapMemoryLimit;
        return this;
    }

    public MainConfigurationProperties withStreamCachingAnySpoolRules(boolean streamCachingAnySpoolRules) {
        this.streamCachingAnySpoolRules = streamCachingAnySpoolRules;
        return this;
    }

    public MainConfigurationProperties withStreamCachingBufferSize(int streamCachingBufferSize) {
        this.streamCachingBufferSize = streamCachingBufferSize;
        return this;
    }

    public MainConfigurationProperties withStreamCachingRemoveSpoolDirectoryWhenStopping(boolean streamCachingRemoveSpoolDirectoryWhenStopping) {
        this.streamCachingRemoveSpoolDirectoryWhenStopping = streamCachingRemoveSpoolDirectoryWhenStopping;
        return this;
    }

    public MainConfigurationProperties withStreamCachingStatisticsEnabled(boolean streamCachingStatisticsEnabled) {
        this.streamCachingStatisticsEnabled = streamCachingStatisticsEnabled;
        return this;
    }

    public MainConfigurationProperties withTracing(boolean tracing) {
        this.tracing = tracing;
        return this;
    }

    public MainConfigurationProperties withMessageHistory(boolean messageHistory) {
        this.messageHistory = messageHistory;
        return this;
    }

    public MainConfigurationProperties withLogMask(boolean logMask) {
        this.logMask = logMask;
        return this;
    }

    public MainConfigurationProperties withLogExhaustedMessageBody(boolean logExhaustedMessageBody) {
        this.logExhaustedMessageBody = logExhaustedMessageBody;
        return this;
    }

    public MainConfigurationProperties withHandleFault(boolean handleFault) {
        this.handleFault = handleFault;
        return this;
    }

    public MainConfigurationProperties withAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
        return this;
    }

    public MainConfigurationProperties withAllowUseOriginalMessage(boolean allowUseOriginalMessage) {
        this.allowUseOriginalMessage = allowUseOriginalMessage;
        return this;
    }

    public MainConfigurationProperties withEndpointRuntimeStatisticsEnabled(boolean endpointRuntimeStatisticsEnabled) {
        this.endpointRuntimeStatisticsEnabled = endpointRuntimeStatisticsEnabled;
        return this;
    }

    public MainConfigurationProperties withUseDataType(boolean useDataType) {
        this.useDataType = useDataType;
        return this;
    }

    public MainConfigurationProperties withUseBreadcrumb(boolean useBreadcrumb) {
        this.useBreadcrumb = useBreadcrumb;
        return this;
    }

    public MainConfigurationProperties withJmxManagementStatisticsLevel(ManagementStatisticsLevel jmxManagementStatisticsLevel) {
        this.jmxManagementStatisticsLevel = jmxManagementStatisticsLevel;
        return this;
    }

    public MainConfigurationProperties withJmxManagementNamePattern(String jmxManagementNamePattern) {
        this.jmxManagementNamePattern = jmxManagementNamePattern;
        return this;
    }

    public MainConfigurationProperties withJmxCreateConnector(boolean jmxCreateConnector) {
        this.jmxCreateConnector = jmxCreateConnector;
        return this;
    }

    public MainConfigurationProperties withUseMdcLogging(boolean useMdcLogging) {
        this.useMdcLogging = useMdcLogging;
        return this;
    }

    public MainConfigurationProperties withThreadNamePattern(String threadNamePattern) {
        this.threadNamePattern = threadNamePattern;
        return this;
    }

    public MainConfigurationProperties withDuration(long duration) {
        this.duration = duration;
        return this;
    }

    public MainConfigurationProperties withHangupInterceptorEnabled(boolean hangupInterceptorEnabled) {
        this.hangupInterceptorEnabled = hangupInterceptorEnabled;
        return this;
    }

    public MainConfigurationProperties withDurationHitExitCode(int durationHitExitCode) {
        this.durationHitExitCode = durationHitExitCode;
        return this;
    }

    public MainConfigurationProperties withFileWatchDirectory(String fileWatchDirectory) {
        this.fileWatchDirectory = fileWatchDirectory;
        return this;
    }

    public MainConfigurationProperties withFileWatchDirectoryRecursively(boolean fileWatchDirectoryRecursively) {
        this.fileWatchDirectoryRecursively = fileWatchDirectoryRecursively;
        return this;
    }

    public MainConfigurationProperties withReloadStrategy(ReloadStrategy reloadStrategy) {
        this.reloadStrategy = reloadStrategy;
        return this;
    }

}
