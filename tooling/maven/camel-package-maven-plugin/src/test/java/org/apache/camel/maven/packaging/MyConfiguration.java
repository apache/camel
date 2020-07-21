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
package org.apache.camel.maven.packaging;

/**
 * Common set of configuration options used by Camel Main, Camel Spring Boot and
 * other runtimes.
 */
public class MyConfiguration {

    private String name;
    private int durationMaxSeconds;
    private int durationMaxIdleSeconds;
    private int durationMaxMessages;
    private int shutdownTimeout = 300;
    private boolean shutdownSuppressLoggingOnTimeout;
    private boolean shutdownNowOnTimeout = true;
    private boolean shutdownRoutesInReverseOrder = true;
    private boolean shutdownLogInflightExchangesOnTimeout = true;
    private String fileConfigurations;
    private boolean jmxEnabled = true;
    private int producerTemplateCacheSize = 1000;
    private int consumerTemplateCacheSize = 1000;
    private boolean loadTypeConverters = true;
    private int logDebugMaxChars;
    private boolean streamCachingEnabled;
    private String streamCachingSpoolDirectory;
    private String streamCachingSpoolCipher;
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
    private boolean autoStartup = true;
    private boolean allowUseOriginalMessage;
    private boolean endpointRuntimeStatisticsEnabled;
    private boolean useDataType;
    private boolean useBreadcrumb;
    private String jmxManagementNamePattern = "#name#";
    private boolean useMdcLogging;
    private String threadNamePattern;
    private String routeFilterIncludePattern;
    private String routeFilterExcludePattern;

    // getter and setters
    // --------------------------------------------------------------

    public String getName() {
        return name;
    }

    /**
     * Sets the name of the CamelContext.
     */
    public void setName(String name) {
        this.name = name;
    }

    public int getDurationMaxSeconds() {
        return durationMaxSeconds;
    }

    /**
     * To specify for how long time in seconds to keep running the JVM before
     * automatic terminating the JVM. You can use this to run Camel for a short
     * while.
     */
    public void setDurationMaxSeconds(int durationMaxSeconds) {
        this.durationMaxSeconds = durationMaxSeconds;
    }

    public int getDurationMaxIdleSeconds() {
        return durationMaxIdleSeconds;
    }

    /**
     * To specify for how long time in seconds Camel can be idle before
     * automatic terminating the JVM. You can use this to run Camel for a short
     * while.
     */
    public void setDurationMaxIdleSeconds(int durationMaxIdleSeconds) {
        this.durationMaxIdleSeconds = durationMaxIdleSeconds;
    }

    public int getDurationMaxMessages() {
        return durationMaxMessages;
    }

    /**
     * To specify how many messages to process by Camel before automatic
     * terminating the JVM. You can use this to run Camel for a short while.
     */
    public void setDurationMaxMessages(int durationMaxMessages) {
        this.durationMaxMessages = durationMaxMessages;
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
     * Whether Camel should try to suppress logging during shutdown and timeout
     * was triggered, meaning forced shutdown is happening. And during forced
     * shutdown we want to avoid logging errors/warnings et all in the logs as a
     * side-effect of the forced timeout. Notice the suppress is a best effort
     * as there may still be some logs coming from 3rd party libraries and
     * whatnot, which Camel cannot control. This option is default false.
     */
    public void setShutdownSuppressLoggingOnTimeout(boolean shutdownSuppressLoggingOnTimeout) {
        this.shutdownSuppressLoggingOnTimeout = shutdownSuppressLoggingOnTimeout;
    }

    public boolean isShutdownNowOnTimeout() {
        return shutdownNowOnTimeout;
    }

    /**
     * Sets whether to force shutdown of all consumers when a timeout occurred
     * and thus not all consumers was shutdown within that period. You should
     * have good reasons to set this option to false as it means that the routes
     * keep running and is halted abruptly when CamelContext has been shutdown.
     */
    public void setShutdownNowOnTimeout(boolean shutdownNowOnTimeout) {
        this.shutdownNowOnTimeout = shutdownNowOnTimeout;
    }

    public boolean isShutdownRoutesInReverseOrder() {
        return shutdownRoutesInReverseOrder;
    }

    /**
     * Sets whether routes should be shutdown in reverse or the same order as
     * they where started.
     */
    public void setShutdownRoutesInReverseOrder(boolean shutdownRoutesInReverseOrder) {
        this.shutdownRoutesInReverseOrder = shutdownRoutesInReverseOrder;
    }

    public boolean isShutdownLogInflightExchangesOnTimeout() {
        return shutdownLogInflightExchangesOnTimeout;
    }

    /**
     * Sets whether to log information about the inflight Exchanges which are
     * still running during a shutdown which didn't complete without the given
     * timeout.
     */
    public void setShutdownLogInflightExchangesOnTimeout(boolean shutdownLogInflightExchangesOnTimeout) {
        this.shutdownLogInflightExchangesOnTimeout = shutdownLogInflightExchangesOnTimeout;
    }

    public String getFileConfigurations() {
        return fileConfigurations;
    }

    /**
     * Directory to load additional configuration files that contains
     * configuration values that takes precedence over any other configuration.
     * This can be used to refer to files that may have secret configuration
     * that has been mounted on the file system for containers. You can specify
     * a pattern to load from sub directories and a name pattern such as
     * /var/app/secret/*.properties, multiple directories can be separated by
     * comma.
     */
    public void setFileConfigurations(String fileConfigurations) {
        this.fileConfigurations = fileConfigurations;
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

    public boolean isLoadTypeConverters() {
        return loadTypeConverters;
    }

    /**
     * Whether to load custom type converters by scanning classpath. This is
     * used for backwards compatibility with Camel 2.x. Its recommended to
     * migrate to use fast type converter loading by setting
     * <tt>@Converter(loader = true)</tt> on your custom type converter classes.
     */
    public void setLoadTypeConverters(boolean loadTypeConverters) {
        this.loadTypeConverters = loadTypeConverters;
    }

    public int getLogDebugMaxChars() {
        return logDebugMaxChars;
    }

    /**
     * Is used to limit the maximum length of the logging Camel message bodies.
     * If the message body is longer than the limit, the log message is clipped.
     * Use -1 to have unlimited length. Use for example 1000 to log at most 1000
     * characters.
     */
    public void setLogDebugMaxChars(int logDebugMaxChars) {
        this.logDebugMaxChars = logDebugMaxChars;
    }

    public boolean isStreamCachingEnabled() {
        return streamCachingEnabled;
    }

    /**
     * Sets whether stream caching is enabled or not. Default is false.
     */
    public void setStreamCachingEnabled(boolean streamCachingEnabled) {
        this.streamCachingEnabled = streamCachingEnabled;
    }

    public String getStreamCachingSpoolDirectory() {
        return streamCachingSpoolDirectory;
    }

    /**
     * Sets the stream caching spool (temporary) directory to use for overflow
     * and spooling to disk. If no spool directory has been explicit configured,
     * then a temporary directory is created in the java.io.tmpdir directory.
     */
    public void setStreamCachingSpoolDirectory(String streamCachingSpoolDirectory) {
        this.streamCachingSpoolDirectory = streamCachingSpoolDirectory;
    }

    public String getStreamCachingSpoolCipher() {
        return streamCachingSpoolCipher;
    }

    /**
     * Sets a stream caching cipher name to use when spooling to disk to write
     * with encryption. By default the data is not encrypted.
     */
    public void setStreamCachingSpoolCipher(String streamCachingSpoolCipher) {
        this.streamCachingSpoolCipher = streamCachingSpoolCipher;
    }

    public long getStreamCachingSpoolThreshold() {
        return streamCachingSpoolThreshold;
    }

    /**
     * Stream caching threshold in bytes when overflow to disk is activated. The
     * default threshold is 128kb. Use -1 to disable overflow to disk.
     */
    public void setStreamCachingSpoolThreshold(long streamCachingSpoolThreshold) {
        this.streamCachingSpoolThreshold = streamCachingSpoolThreshold;
    }

    public int getStreamCachingSpoolUsedHeapMemoryThreshold() {
        return streamCachingSpoolUsedHeapMemoryThreshold;
    }

    /**
     * Sets a percentage (1-99) of used heap memory threshold to activate stream
     * caching spooling to disk.
     */
    public void setStreamCachingSpoolUsedHeapMemoryThreshold(int streamCachingSpoolUsedHeapMemoryThreshold) {
        this.streamCachingSpoolUsedHeapMemoryThreshold = streamCachingSpoolUsedHeapMemoryThreshold;
    }

    public String getStreamCachingSpoolUsedHeapMemoryLimit() {
        return streamCachingSpoolUsedHeapMemoryLimit;
    }

    /**
     * Sets what the upper bounds should be when
     * streamCachingSpoolUsedHeapMemoryThreshold is in use.
     */
    public void setStreamCachingSpoolUsedHeapMemoryLimit(String streamCachingSpoolUsedHeapMemoryLimit) {
        this.streamCachingSpoolUsedHeapMemoryLimit = streamCachingSpoolUsedHeapMemoryLimit;
    }

    public boolean isStreamCachingAnySpoolRules() {
        return streamCachingAnySpoolRules;
    }

    /**
     * Sets whether if just any of the
     * org.apache.camel.spi.StreamCachingStrategy.SpoolRule rules returns true
     * then shouldSpoolCache(long) returns true, to allow spooling to disk. If
     * this option is false, then all the
     * org.apache.camel.spi.StreamCachingStrategy.SpoolRule must return true.
     * The default value is false which means that all the rules must return
     * true.
     */
    public void setStreamCachingAnySpoolRules(boolean streamCachingAnySpoolRules) {
        this.streamCachingAnySpoolRules = streamCachingAnySpoolRules;
    }

    public int getStreamCachingBufferSize() {
        return streamCachingBufferSize;
    }

    /**
     * Sets the stream caching buffer size to use when allocating in-memory
     * buffers used for in-memory stream caches. The default size is 4096.
     */
    public void setStreamCachingBufferSize(int streamCachingBufferSize) {
        this.streamCachingBufferSize = streamCachingBufferSize;
    }

    public boolean isStreamCachingRemoveSpoolDirectoryWhenStopping() {
        return streamCachingRemoveSpoolDirectoryWhenStopping;
    }

    /**
     * Whether to remove stream caching temporary directory when stopping. This
     * option is default true.
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
     * Sets whether tracing is enabled or not. Default is false.
     */
    @Deprecated
    public void setTracing(boolean tracing) {
        this.tracing = tracing;
    }

    public boolean isMessageHistory() {
        return messageHistory;
    }

    /**
     * Sets whether message history is enabled or not. Default is true.
     */
    public void setMessageHistory(boolean messageHistory) {
        this.messageHistory = messageHistory;
    }

    public boolean isLogMask() {
        return logMask;
    }

    /**
     * Sets whether log mask is enabled or not. Default is false.
     */
    public void setLogMask(boolean logMask) {
        this.logMask = logMask;
    }

    public boolean isLogExhaustedMessageBody() {
        return logExhaustedMessageBody;
    }

    /**
     * Sets whether to log exhausted message body with message history. Default
     * is false.
     */
    public void setLogExhaustedMessageBody(boolean logExhaustedMessageBody) {
        this.logExhaustedMessageBody = logExhaustedMessageBody;
    }

    public boolean isAutoStartup() {
        return autoStartup;
    }

    /**
     * Sets whether the object should automatically start when Camel starts.
     * Important: Currently only routes can be disabled, as CamelContext's are
     * always started. Note: When setting auto startup false on CamelContext
     * then that takes precedence and no routes is started. You would need to
     * start CamelContext explicit using the
     * org.apache.camel.CamelContext.start() method, to start the context, and
     * then you would need to start the routes manually using
     * CamelContext.getRouteController().startRoute(String). Default is true to
     * always start up.
     */
    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public boolean isAllowUseOriginalMessage() {
        return allowUseOriginalMessage;
    }

    /**
     * Sets whether to allow access to the original message from Camel's error
     * handler, or from org.apache.camel.spi.UnitOfWork.getOriginalInMessage().
     * Turning this off can optimize performance, as defensive copy of the
     * original message is not needed. Default is false.
     */
    public void setAllowUseOriginalMessage(boolean allowUseOriginalMessage) {
        this.allowUseOriginalMessage = allowUseOriginalMessage;
    }

    public boolean isEndpointRuntimeStatisticsEnabled() {
        return endpointRuntimeStatisticsEnabled;
    }

    /**
     * Sets whether endpoint runtime statistics is enabled (gathers runtime
     * usage of each incoming and outgoing endpoints). The default value is
     * false.
     */
    public void setEndpointRuntimeStatisticsEnabled(boolean endpointRuntimeStatisticsEnabled) {
        this.endpointRuntimeStatisticsEnabled = endpointRuntimeStatisticsEnabled;
    }

    public boolean isUseDataType() {
        return useDataType;
    }

    /**
     * Whether to enable using data type on Camel messages. Data type are
     * automatic turned on if one ore more routes has been explicit configured
     * with input and output types. Otherwise data type is default off.
     */
    public void setUseDataType(boolean useDataType) {
        this.useDataType = useDataType;
    }

    public boolean isUseBreadcrumb() {
        return useBreadcrumb;
    }

    /**
     * Set whether breadcrumb is enabled. The default value is false.
     */
    public void setUseBreadcrumb(boolean useBreadcrumb) {
        this.useBreadcrumb = useBreadcrumb;
    }

    public String getJmxManagementNamePattern() {
        return jmxManagementNamePattern;
    }

    /**
     * The naming pattern for creating the CamelContext JMX management name. The
     * default pattern is #name#
     */
    public void setJmxManagementNamePattern(String jmxManagementNamePattern) {
        this.jmxManagementNamePattern = jmxManagementNamePattern;
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
     * Sets the thread name pattern used for creating the full thread name. The
     * default pattern is: Camel (#camelId#) thread ##counter# - #name# Where
     * #camelId# is the name of the CamelContext. and #counter# is a unique
     * incrementing counter. and #name# is the regular thread name. You can also
     * use #longName# which is the long thread name which can includes endpoint
     * parameters etc.
     */
    public void setThreadNamePattern(String threadNamePattern) {
        this.threadNamePattern = threadNamePattern;
    }

    public String getRouteFilterIncludePattern() {
        return routeFilterIncludePattern;
    }

    /**
     * Used for filtering routes routes matching the given pattern, which
     * follows the following rules: - Match by route id - Match by route input
     * endpoint uri The matching is using exact match, by wildcard and regular
     * expression as documented by PatternHelper#matchPattern(String, String).
     * For example to only include routes which starts with foo in their route
     * id's, use: include=foo&#42; And to exclude routes which starts from JMS
     * endpoints, use: exclude=jms:&#42; Multiple patterns can be separated by
     * comma, for example to exclude both foo and bar routes, use:
     * exclude=foo&#42;,bar&#42; Exclude takes precedence over include.
     */
    public void setRouteFilterIncludePattern(String include) {
        this.routeFilterIncludePattern = include;
    }

    public String getRouteFilterExcludePattern() {
        return routeFilterExcludePattern;
    }

    /**
     * Used for filtering routes routes matching the given pattern, which
     * follows the following rules: - Match by route id - Match by route input
     * endpoint uri The matching is using exact match, by wildcard and regular
     * expression as documented by PatternHelper#matchPattern(String, String).
     * For example to only include routes which starts with foo in their route
     * id's, use: include=foo&#42; And to exclude routes which starts from JMS
     * endpoints, use: exclude=jms:&#42; Multiple patterns can be separated by
     * comma, for example to exclude both foo and bar routes, use:
     * exclude=foo&#42;,bar&#42; Exclude takes precedence over include.
     */
    public void setRouteFilterExcludePattern(String exclude) {
        this.routeFilterExcludePattern = exclude;
    }

}
