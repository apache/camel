/**
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
package org.apache.camel.spring.boot;

import org.apache.camel.ManagementStatisticsLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.springboot")
public class CamelConfigurationProperties {

    // Properties

    /**
     * Sets the name of the CamelContext.
     */
    private String name;

    /**
     * Timeout in seconds to graceful shutdown Camel.
     */
    private int shutdownTimeout = 300;

    /**
     * Whether Camel should try to suppress logging during shutdown and timeout was triggered,
     * meaning forced shutdown is happening. And during forced shutdown we want to avoid logging
     * errors/warnings et all in the logs as a side-effect of the forced timeout.
     * <p/>
     * By default this is <tt>false</tt>
     * <p/>
     * Notice the suppress is a <i>best effort</i> as there may still be some logs coming
     * from 3rd party libraries and whatnot, which Camel cannot control.
     */
    private boolean shutdownSuppressLoggingOnTimeout;

    /**
     * Sets whether to force shutdown of all consumers when a timeout occurred and thus
     * not all consumers was shutdown within that period.
     * <p/>
     * You should have good reasons to set this option to <tt>false</tt> as it means that the routes
     * keep running and is halted abruptly when CamelContext has been shutdown.
     */
    private boolean shutdownNowOnTimeout = true;

    /**
     * Sets whether routes should be shutdown in reverse or the same order as they where started.
     */
    private boolean shutdownRoutesInReverseOrder = true;

    /**
     * Sets whether to log information about the inflight Exchanges which are still running
     * during a shutdown which didn't complete without the given timeout.
     */
    private boolean shutdownLogInflightExchangesOnTimeout = true;

    /**
     * Enable JMX in your Camel application.
     */
    private boolean jmxEnabled = true;

    /**
     * Producer template endpoints cache size.
     */
    private int producerTemplateCacheSize = 1000;

    /**
     * Consumer template endpoints cache size.
     */
    private int consumerTemplateCacheSize = 1000;

    /**
     * Enables enhanced Camel/Spring type conversion.
     */
    private boolean typeConversion = true;

    /**
     * Sets whether to load custom type converters by scanning classpath.
     * This can be turned off if you are only using Camel components
     * that does not provide type converters which is needed at runtime.
     * In such situations setting this option to false, can speedup starting
     * Camel.
     */
    private boolean loadTypeConverters = true;

    /**
     * Used for inclusive filtering component scanning of RouteBuilder classes with @Component annotation.
     * The exclusive filtering takes precedence over inclusive filtering.
     * The pattern is using Ant-path style pattern.
     * <p/>
     * Multiple patterns can be specified separated by comma.
     * For example to include all classes starting with Foo use <tt>&#42;&#42;/Foo*</tt>.
     * To include all routes form a specific package use, <tt>com/mycompany/foo/*</tt>
     * To include all routes form a specific package and its sub-packages use double wildcards, <tt>com/mycompany/foo/**</tt>
     * And to include all routes from two specific packages use, <tt>com/mycompany/foo/*,com/mycompany/stuff/*</tt>
     *
     * @see org.springframework.util.AntPathMatcher
     */
    private String javaRoutesIncludePattern;

    /**
     * Used for exclusive filtering component scanning of RouteBuilder classes with @Component annotation.
     * The exclusive filtering takes precedence over inclusive filtering.
     * The pattern is using Ant-path style pattern.
     * Multiple patterns can be specified separated by comma.
     * <p/>
     * For example to exclude all classes starting with Bar use <tt>&#42;&#42;/Bar*</tt>.
     * To exclude all routes form a specific package use, <tt>com/mycompany/bar/*</tt>
     * To exclude all routes form a specific package and its sub-packages use double wildcards, <tt>com/mycompany/bar/**</tt>
     * And to exclude all routes from two specific packages use, <tt>com/mycompany/bar/*,com/mycompany/stuff/*</tt>
     *
     * @see org.springframework.util.AntPathMatcher
     */
    private String javaRoutesExcludePattern;

    /**
     * Directory to scan for adding additional XML routes.
     * You can turn this off by setting the value to false.
     */
    private String xmlRoutes = "classpath:camel/*.xml";

    /**
     * Directory to scan for adding additional XML rests.
     * You can turn this off by setting the value to false.
     */
    private String xmlRests = "classpath:camel-rest/*.xml";

    /**
     * To watch the directory for file changes which triggers
     * a live reload of the Camel routes on-the-fly.
     * <p/>
     * For example configure this to point to the source code where the Camel XML files are located
     * such as: src/main/resources/camel/
     */
    private String xmlRoutesReloadDirectory;

    /**
     * Directory to load additional configuration files that contains
     * configuration values that takes precedence over any other configuration.
     * This can be used to refer to files that may have secret configuration that
     * has been mounted on the file system for containers.
     * <p/>
     * You must use either <tt>file:</tt> or <tt>classpath:</tt> as prefix to load
     * from file system or classpath. Then you can specify a pattern to load
     * from sub directories and a name pattern such as <tt>file:/var/app/secret/*.properties</tt>
     */
    private String fileConfigurations;

    /**
     * Whether to use the main run controller to ensure the Spring-Boot application
     * keeps running until being stopped or the JVM terminated.
     * You typically only need this if you run Spring-Boot standalone.
     * If you run Spring-Boot with spring-boot-starter-web then the web container keeps the JVM running.
     */
    private boolean mainRunController;

    /**
     * To specify for how long time in seconds to keep running the JVM before automatic terminating the JVM.
     * You can use this to run Spring Boot for a short while.
     */
    private int durationMaxSeconds;

    /**
     * To specify for how long time in seconds Camel can be idle before automatic terminating the JVM.
     * You can use this to run Spring Boot for a short while.
     */
    private int durationMaxIdleSeconds;

    /**
     * To specify how many messages to process by Camel before automatic terminating the JVM.
     * You can use this to run Spring Boot for a short while.
     */
    private int durationMaxMessages;

    /**
     * Whether to include non-singleton beans (prototypes) when scanning for RouteBuilder instances.
     * By default only singleton beans is included in the context scan.
     */
    private boolean includeNonSingletons;

    /**
     * Is used to limit the maximum length of the logging Camel message bodies. If the message body
     * is longer than the limit, the log message is clipped. Use -1 to have unlimited length.
     * Use for example 1000 to log at most 1000 characters.
     */
    private int logDebugMaxChars;

    /**
     * Sets whether stream caching is enabled or not.
     *
     * Default is false.
     *
     * @deprecated use {@link #streamCachingEnabled}
     */
    @Deprecated
    private boolean streamCaching;

    /**
     * Sets whether stream caching is enabled or not.
     *
     * Default is false.
     */
    private boolean streamCachingEnabled;

    /**
     * Sets the stream caching spool (temporary) directory to use for overflow and spooling to disk.
     * <p/>
     * If no spool directory has been explicit configured, then a temporary directory
     * is created in the <tt>java.io.tmpdir</tt> directory.
     */
    private String streamCachingSpoolDirectory;

    /**
     * Sets a stream caching chiper name to use when spooling to disk to write with encryption.
     * <p/>
     * By default the data is not encrypted.
     */
    private String streamCachingSpoolChiper;

    /**
     * Stream caching threshold in bytes when overflow to disk is activated.
     * <p/>
     * The default threshold is {@link org.apache.camel.StreamCache#DEFAULT_SPOOL_THRESHOLD} bytes (eg 128kb).
     * Use <tt>-1</tt> to disable overflow to disk.
     */
    private long streamCachingSpoolThreshold;

    /**
     * Sets a percentage (1-99) of used heap memory threshold to activate stream caching spooling to disk.
     */
    private int streamCachingSpoolUsedHeapMemoryThreshold;

    /**
     * Sets what the upper bounds should be when streamCachingSpoolUsedHeapMemoryThreshold is in use.
     */
    private String streamCachingSpoolUsedHeapMemoryLimit;

    /**
     * Sets whether if just any of the {@link org.apache.camel.spi.StreamCachingStrategy.SpoolRule} rules
     * returns <tt>true</tt> then shouldSpoolCache(long) returns <tt>true</tt>.
     * If this option is <tt>false</tt>, then <b>all</b> the {@link org.apache.camel.spi.StreamCachingStrategy.SpoolRule} must
     * return <tt>true</tt>.
     * <p/>
     * The default value is <tt>false</tt> which means that all the rules must return <tt>true</tt>.
     */
    private boolean streamCachingAnySpoolRules;

    /**
     * Sets the stream caching buffer size to use when allocating in-memory buffers used for in-memory stream caches.
     * <p/>
     * The default size is {@link org.apache.camel.util.IOHelper#DEFAULT_BUFFER_SIZE}
     */
    private int streamCachingBufferSize;

    /**
     * Whether to remove stream caching temporary directory when stopping.
     * <p/>
     * This option is default <tt>true</tt>
     */
    private boolean streamCachingRemoveSpoolDirectoryWhenStopping = true;

    /**
     * Sets whether stream caching statistics is enabled.
     */
    private boolean streamCachingStatisticsEnabled;

    /**
     * Sets whether tracing is enabled or not.
     *
     * Default is false.
     */
    private boolean tracing;

    /**
     * Sets whether message history is enabled or not.
     *
     * Default is true.
     */
    private boolean messageHistory = true;

    /**
     * Sets whether log mask is enabled or not.
     *
     * Default is false.
     */
    private boolean logMask;

    /**
     * Sets whether to log exhausted message body with message history.
     *
     * Default is false.
     */
    private boolean logExhaustedMessageBody;

    /**
     * Sets whether fault handling is enabled or not.
     *
     * Default is false.
     */
    private boolean handleFault;

    /**
     * Sets whether the object should automatically start when Camel starts.
     * Important: Currently only routes can be disabled, as CamelContext's are always started.
     * Note: When setting auto startup false on CamelContext then that takes precedence
     * and no routes is started. You would need to start CamelContext explicit using
     * the org.apache.camel.CamelContext.start() method, to start the context, and then
     * you would need to start the routes manually using CamelContext.startRoute(String).
     *
     * Default is true to always start up.
     */
    private boolean autoStartup = true;

    /**
     * Sets whether to allow access to the original message from Camel's error handler,
     * or from org.apache.camel.spi.UnitOfWork.getOriginalInMessage().
     * Turning this off can optimize performance, as defensive copy of the original message is not needed.
     *
     * Default is false.
     */
    private boolean allowUseOriginalMessage;

    /**
     * Sets whether endpoint runtime statistics is enabled (gathers runtime usage of each incoming and outgoing endpoints).
     *
     * The default value is false.
     */
    private boolean endpointRuntimeStatisticsEnabled;

    /**
     * Whether to enable using data type on Camel messages.
     * <p/>
     * Data type are automatic turned on if one ore more routes has been explicit configured with input and output types.
     * Otherwise data type is default off.
     */
    private boolean useDataType;

    /**
     * Set whether breadcrumb is enabled.
     */
    private boolean useBreadcrumb = true;

    /**
     * Sets the JMX statistics level
     * The level can be set to Extended to gather additional information
     *
     * The default value is Default.
     */
    private ManagementStatisticsLevel jmxManagementStatisticsLevel = ManagementStatisticsLevel.Default;

    /**
     * The naming pattern for creating the CamelContext JMX management name.
     *
     * The default pattern is #name#
     */
    private String jmxManagementNamePattern = "#name#";

    /**
     * Whether JMX connector is created, allowing clients to connect remotely
     *
     * The default value is false.
     */
    private boolean jmxCreateConnector;

    /**
     * Tracer should output message body
     */
    private boolean traceFormatterShowBody = true;

    /**
     * Tracer should output message body type
     */
    private boolean tracerFormatterShowBodyType = true;

    /**
     * Tracer should output breadcrumb
     */
    private boolean traceFormatterShowBreadCrumb = true;

    /**
     * Tracer should output exchange id
     */
    private boolean traceFormatterShowExchangeId;

    /**
     * Tracer should output message headers
     */
    private boolean traceFormatterShowHeaders = true;

    /**
     * Tracer should output exchange properties
     */
    private boolean traceFormatterShowProperties;

    /**
     * Tracer should output EIP node
     */
    private boolean traceFormatterShowNode = true;

    /**
     * Tracer should output message exchange pattern (MEP)
     */
    private boolean traceFormatterShowExchangePattern = true;

    /**
     * Tracer should output exception
     */
    private boolean traceFormatterShowException = true;

    /**
     * Tracer should output route id
     */
    private boolean traceFormatterShowRouteId = true;

    /**
     * Tracer maximum length of breadcrumb ids
     */
    private Integer tracerFormatterBreadCrumbLength;

    /**
     * Tracer should output short exchange id
     */
    private boolean traceFormatterShowShortExchangeId;

    /**
     * Tracer maximum length of node
     */
    private Integer tracerFormatterNodeLength;

    /**
     * Tracer maximum characters in total
     */
    private Integer tracerFormatterMaxChars = 10000;
    
    /**
     * To turn on MDC logging
     */
    private boolean useMDCLogging;

    // Getters & setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getShutdownTimeout() {
        return shutdownTimeout;
    }

    public void setShutdownTimeout(int shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    public boolean isShutdownSuppressLoggingOnTimeout() {
        return shutdownSuppressLoggingOnTimeout;
    }

    public void setShutdownSuppressLoggingOnTimeout(boolean shutdownSuppressLoggingOnTimeout) {
        this.shutdownSuppressLoggingOnTimeout = shutdownSuppressLoggingOnTimeout;
    }

    public boolean isShutdownNowOnTimeout() {
        return shutdownNowOnTimeout;
    }

    public void setShutdownNowOnTimeout(boolean shutdownNowOnTimeout) {
        this.shutdownNowOnTimeout = shutdownNowOnTimeout;
    }

    public boolean isShutdownRoutesInReverseOrder() {
        return shutdownRoutesInReverseOrder;
    }

    public void setShutdownRoutesInReverseOrder(boolean shutdownRoutesInReverseOrder) {
        this.shutdownRoutesInReverseOrder = shutdownRoutesInReverseOrder;
    }

    public boolean isShutdownLogInflightExchangesOnTimeout() {
        return shutdownLogInflightExchangesOnTimeout;
    }

    public void setShutdownLogInflightExchangesOnTimeout(boolean shutdownLogInflightExchangesOnTimeout) {
        this.shutdownLogInflightExchangesOnTimeout = shutdownLogInflightExchangesOnTimeout;
    }

    public boolean isJmxEnabled() {
        return jmxEnabled;
    }

    public void setJmxEnabled(boolean jmxEnabled) {
        this.jmxEnabled = jmxEnabled;
    }

    public int getProducerTemplateCacheSize() {
        return producerTemplateCacheSize;
    }

    public void setProducerTemplateCacheSize(int producerTemplateCacheSize) {
        this.producerTemplateCacheSize = producerTemplateCacheSize;
    }

    public int getConsumerTemplateCacheSize() {
        return consumerTemplateCacheSize;
    }

    public void setConsumerTemplateCacheSize(int consumerTemplateCacheSize) {
        this.consumerTemplateCacheSize = consumerTemplateCacheSize;
    }

    public boolean isTypeConversion() {
        return typeConversion;
    }

    public void setTypeConversion(boolean typeConversion) {
        this.typeConversion = typeConversion;
    }

    public boolean isLoadTypeConverters() {
        return loadTypeConverters;
    }

    public void setLoadTypeConverters(boolean loadTypeConverters) {
        this.loadTypeConverters = loadTypeConverters;
    }

    public String getJavaRoutesIncludePattern() {
        return javaRoutesIncludePattern;
    }

    public void setJavaRoutesIncludePattern(String javaRoutesIncludePattern) {
        this.javaRoutesIncludePattern = javaRoutesIncludePattern;
    }

    public String getJavaRoutesExcludePattern() {
        return javaRoutesExcludePattern;
    }

    public void setJavaRoutesExcludePattern(String javaRoutesExcludePattern) {
        this.javaRoutesExcludePattern = javaRoutesExcludePattern;
    }

    public String getXmlRoutes() {
        return xmlRoutes;
    }

    public void setXmlRoutes(String xmlRoutes) {
        this.xmlRoutes = xmlRoutes;
    }

    public String getXmlRests() {
        return xmlRests;
    }

    public void setXmlRests(String xmlRests) {
        this.xmlRests = xmlRests;
    }

    public String getXmlRoutesReloadDirectory() {
        return xmlRoutesReloadDirectory;
    }

    public void setXmlRoutesReloadDirectory(String xmlRoutesReloadDirectory) {
        this.xmlRoutesReloadDirectory = xmlRoutesReloadDirectory;
    }

    public boolean isMainRunController() {
        return mainRunController;
    }

    public void setMainRunController(boolean mainRunController) {
        this.mainRunController = mainRunController;
    }

    public int getDurationMaxSeconds() {
        return durationMaxSeconds;
    }

    public void setDurationMaxSeconds(int durationMaxSeconds) {
        this.durationMaxSeconds = durationMaxSeconds;
    }

    public int getDurationMaxIdleSeconds() {
        return durationMaxIdleSeconds;
    }

    public void setDurationMaxIdleSeconds(int durationMaxIdleSeconds) {
        this.durationMaxIdleSeconds = durationMaxIdleSeconds;
    }

    public int getDurationMaxMessages() {
        return durationMaxMessages;
    }

    public void setDurationMaxMessages(int durationMaxMessages) {
        this.durationMaxMessages = durationMaxMessages;
    }

    public int getLogDebugMaxChars() {
        return logDebugMaxChars;
    }

    public void setLogDebugMaxChars(int logDebugMaxChars) {
        this.logDebugMaxChars = logDebugMaxChars;
    }

    @Deprecated
    public boolean isStreamCaching() {
        return streamCachingEnabled;
    }

    @Deprecated
    public void setStreamCaching(boolean streamCaching) {
        this.streamCachingEnabled = streamCaching;
    }

    public boolean isStreamCachingEnabled() {
        return streamCachingEnabled;
    }

    public void setStreamCachingEnabled(boolean streamCachingEnabled) {
        this.streamCachingEnabled = streamCachingEnabled;
    }

    public String getStreamCachingSpoolDirectory() {
        return streamCachingSpoolDirectory;
    }

    public void setStreamCachingSpoolDirectory(String streamCachingSpoolDirectory) {
        this.streamCachingSpoolDirectory = streamCachingSpoolDirectory;
    }

    public String getStreamCachingSpoolChiper() {
        return streamCachingSpoolChiper;
    }

    public void setStreamCachingSpoolChiper(String streamCachingSpoolChiper) {
        this.streamCachingSpoolChiper = streamCachingSpoolChiper;
    }

    public long getStreamCachingSpoolThreshold() {
        return streamCachingSpoolThreshold;
    }

    public void setStreamCachingSpoolThreshold(long streamCachingSpoolThreshold) {
        this.streamCachingSpoolThreshold = streamCachingSpoolThreshold;
    }

    public int getStreamCachingSpoolUsedHeapMemoryThreshold() {
        return streamCachingSpoolUsedHeapMemoryThreshold;
    }

    public void setStreamCachingSpoolUsedHeapMemoryThreshold(int streamCachingSpoolUsedHeapMemoryThreshold) {
        this.streamCachingSpoolUsedHeapMemoryThreshold = streamCachingSpoolUsedHeapMemoryThreshold;
    }

    public String getStreamCachingSpoolUsedHeapMemoryLimit() {
        return streamCachingSpoolUsedHeapMemoryLimit;
    }

    public void setStreamCachingSpoolUsedHeapMemoryLimit(String streamCachingSpoolUsedHeapMemoryLimit) {
        this.streamCachingSpoolUsedHeapMemoryLimit = streamCachingSpoolUsedHeapMemoryLimit;
    }

    public boolean isStreamCachingAnySpoolRules() {
        return streamCachingAnySpoolRules;
    }

    public void setStreamCachingAnySpoolRules(boolean streamCachingAnySpoolRules) {
        this.streamCachingAnySpoolRules = streamCachingAnySpoolRules;
    }

    public int getStreamCachingBufferSize() {
        return streamCachingBufferSize;
    }

    public void setStreamCachingBufferSize(int streamCachingBufferSize) {
        this.streamCachingBufferSize = streamCachingBufferSize;
    }

    public boolean isStreamCachingRemoveSpoolDirectoryWhenStopping() {
        return streamCachingRemoveSpoolDirectoryWhenStopping;
    }

    public void setStreamCachingRemoveSpoolDirectoryWhenStopping(boolean streamCachingRemoveSpoolDirectoryWhenStopping) {
        this.streamCachingRemoveSpoolDirectoryWhenStopping = streamCachingRemoveSpoolDirectoryWhenStopping;
    }

    public boolean isStreamCachingStatisticsEnabled() {
        return streamCachingStatisticsEnabled;
    }

    public void setStreamCachingStatisticsEnabled(boolean streamCachingStatisticsEnabled) {
        this.streamCachingStatisticsEnabled = streamCachingStatisticsEnabled;
    }

    public boolean isTracing() {
        return tracing;
    }

    public void setTracing(boolean tracing) {
        this.tracing = tracing;
    }

    public boolean isMessageHistory() {
        return messageHistory;
    }

    public void setMessageHistory(boolean messageHistory) {
        this.messageHistory = messageHistory;
    }

    public boolean isLogMask() {
        return logMask;
    }

    public void setLogMask(boolean logMask) {
        this.logMask = logMask;
    }

    public boolean isLogExhaustedMessageBody() {
        return logExhaustedMessageBody;
    }

    public void setLogExhaustedMessageBody(boolean logExhaustedMessageBody) {
        this.logExhaustedMessageBody = logExhaustedMessageBody;
    }

    public boolean isHandleFault() {
        return handleFault;
    }

    public void setHandleFault(boolean handleFault) {
        this.handleFault = handleFault;
    }

    public boolean isAutoStartup() {
        return autoStartup;
    }

    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public boolean isAllowUseOriginalMessage() {
        return allowUseOriginalMessage;
    }

    public void setAllowUseOriginalMessage(boolean allowUseOriginalMessage) {
        this.allowUseOriginalMessage = allowUseOriginalMessage;
    }

    public boolean isEndpointRuntimeStatisticsEnabled() {
        return endpointRuntimeStatisticsEnabled;
    }

    public void setEndpointRuntimeStatisticsEnabled(boolean endpointRuntimeStatisticsEnabled) {
        this.endpointRuntimeStatisticsEnabled = endpointRuntimeStatisticsEnabled;
    }

    public boolean isUseDataType() {
        return useDataType;
    }

    public void setUseDataType(boolean useDataType) {
        this.useDataType = useDataType;
    }

    public boolean isUseBreadcrumb() {
        return useBreadcrumb;
    }

    public void setUseBreadcrumb(boolean useBreadcrumb) {
        this.useBreadcrumb = useBreadcrumb;
    }

    public ManagementStatisticsLevel getJmxManagementStatisticsLevel() {
        return jmxManagementStatisticsLevel;
    }

    public void setJmxManagementStatisticsLevel(ManagementStatisticsLevel jmxManagementStatisticsLevel) {
        this.jmxManagementStatisticsLevel = jmxManagementStatisticsLevel;
    }

    public String getJmxManagementNamePattern() {
        return jmxManagementNamePattern;
    }

    public void setJmxManagementNamePattern(String jmxManagementNamePattern) {
        this.jmxManagementNamePattern = jmxManagementNamePattern;
    }

    public boolean isJmxCreateConnector() {
        return jmxCreateConnector;
    }

    public void setJmxCreateConnector(boolean jmxCreateConnector) {
        this.jmxCreateConnector = jmxCreateConnector;
    }

    public String getFileConfigurations() {
        return fileConfigurations;
    }

    public void setFileConfigurations(String fileConfigurations) {
        this.fileConfigurations = fileConfigurations;
    }

    public boolean isTraceFormatterShowBody() {
        return traceFormatterShowBody;
    }

    public void setTraceFormatterShowBody(boolean traceFormatterShowBody) {
        this.traceFormatterShowBody = traceFormatterShowBody;
    }

    public boolean isTracerFormatterShowBodyType() {
        return tracerFormatterShowBodyType;
    }

    public void setTracerFormatterShowBodyType(boolean tracerFormatterShowBodyType) {
        this.tracerFormatterShowBodyType = tracerFormatterShowBodyType;
    }

    public boolean isTraceFormatterShowBreadCrumb() {
        return traceFormatterShowBreadCrumb;
    }

    public void setTraceFormatterShowBreadCrumb(boolean traceFormatterShowBreadCrumb) {
        this.traceFormatterShowBreadCrumb = traceFormatterShowBreadCrumb;
    }

    public boolean isTraceFormatterShowExchangeId() {
        return traceFormatterShowExchangeId;
    }

    public void setTraceFormatterShowExchangeId(boolean traceFormatterShowExchangeId) {
        this.traceFormatterShowExchangeId = traceFormatterShowExchangeId;
    }

    public boolean isTraceFormatterShowHeaders() {
        return traceFormatterShowHeaders;
    }

    public void setTraceFormatterShowHeaders(boolean traceFormatterShowHeaders) {
        this.traceFormatterShowHeaders = traceFormatterShowHeaders;
    }

    public boolean isTraceFormatterShowProperties() {
        return traceFormatterShowProperties;
    }

    public void setTraceFormatterShowProperties(boolean traceFormatterShowProperties) {
        this.traceFormatterShowProperties = traceFormatterShowProperties;
    }

    public boolean isTraceFormatterShowNode() {
        return traceFormatterShowNode;
    }

    public void setTraceFormatterShowNode(boolean traceFormatterShowNode) {
        this.traceFormatterShowNode = traceFormatterShowNode;
    }

    public boolean isTraceFormatterShowExchangePattern() {
        return traceFormatterShowExchangePattern;
    }

    public void setTraceFormatterShowExchangePattern(boolean traceFormatterShowExchangePattern) {
        this.traceFormatterShowExchangePattern = traceFormatterShowExchangePattern;
    }

    public boolean isTraceFormatterShowException() {
        return traceFormatterShowException;
    }

    public void setTraceFormatterShowException(boolean traceFormatterShowException) {
        this.traceFormatterShowException = traceFormatterShowException;
    }

    public boolean isTraceFormatterShowRouteId() {
        return traceFormatterShowRouteId;
    }

    public void setTraceFormatterShowRouteId(boolean traceFormatterShowRouteId) {
        this.traceFormatterShowRouteId = traceFormatterShowRouteId;
    }

    public Integer getTracerFormatterBreadCrumbLength() {
        return tracerFormatterBreadCrumbLength;
    }

    public void setTracerFormatterBreadCrumbLength(Integer tracerFormatterBreadCrumbLength) {
        this.tracerFormatterBreadCrumbLength = tracerFormatterBreadCrumbLength;
    }

    public boolean isTraceFormatterShowShortExchangeId() {
        return traceFormatterShowShortExchangeId;
    }

    public void setTraceFormatterShowShortExchangeId(boolean traceFormatterShowShortExchangeId) {
        this.traceFormatterShowShortExchangeId = traceFormatterShowShortExchangeId;
    }

    public Integer getTracerFormatterNodeLength() {
        return tracerFormatterNodeLength;
    }

    public void setTracerFormatterNodeLength(Integer tracerFormatterNodeLength) {
        this.tracerFormatterNodeLength = tracerFormatterNodeLength;
    }

    public Integer getTracerFormatterMaxChars() {
        return tracerFormatterMaxChars;
    }

    public void setTracerFormatterMaxChars(Integer tracerFormatterMaxChars) {
        this.tracerFormatterMaxChars = tracerFormatterMaxChars;
    }

    public boolean isIncludeNonSingletons() {
        return includeNonSingletons;
    }

    public void setIncludeNonSingletons(boolean includeNonSingletons) {
        this.includeNonSingletons = includeNonSingletons;
    }
    
    public boolean isUseMDCLogging() {
        return useMDCLogging;
    }
    
    public void setUseMDCLogging(boolean useMDCLogging) {
        this.useMDCLogging = useMDCLogging;
    }
}
