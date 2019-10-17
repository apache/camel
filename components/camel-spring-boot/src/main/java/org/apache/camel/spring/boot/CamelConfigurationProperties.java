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
package org.apache.camel.spring.boot;

import org.apache.camel.LoggingLevel;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.main.DefaultConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.springboot")
public class CamelConfigurationProperties extends DefaultConfigurationProperties<CamelConfigurationProperties> {

    // Spring Boot only Properties
    // ---------------------------

    /**
     * Whether to use the main run controller to ensure the Spring-Boot application
     * keeps running until being stopped or the JVM terminated.
     * You typically only need this if you run Spring-Boot standalone.
     * If you run Spring-Boot with spring-boot-starter-web then the web container keeps the JVM running.
     */
    private boolean mainRunController;

    /**
     * Whether to include non-singleton beans (prototypes) when scanning for RouteBuilder instances.
     * By default only singleton beans is included in the context scan.
     */
    private boolean includeNonSingletons;

    /**
     * Whether to log a WARN if Camel on Spring Boot was immediately shutdown after starting which
     * very likely is because there is no JVM thread to keep the application running.
     */
    private boolean warnOnEarlyShutdown = true;

    /**
     * Directory to scan for adding additional XML routes.
     * You can turn this off by setting the value to false.
     *
     * Files can be loaded from either classpath or file by prefixing with classpath: or file:
     * Wildcards is supported using a ANT pattern style paths, such as classpath:&#42;&#42;/&#42;camel&#42;.xml
     *
     * Multiple directories can be specified and separated by comma, such as:
     * file:/myapp/mycamel/&#42;.xml,file:/myapp/myothercamel/&#42;.xml
     */
    private String xmlRoutes = "classpath:camel/*.xml";

    /**
     * Directory to scan for adding additional XML rests.
     * You can turn this off by setting the value to false.
     *
     * Files can be loaded from either classpath or file by prefixing with classpath: or file:
     * Wildcards is supported using a ANT pattern style paths, such as classpath:&#42;&#42;/&#42;camel&#42;.xml
     *
     * Multiple directories can be specified and separated by comma, such as:
     * file:/myapp/mycamel/&#42;.xml,file:/myapp/myothercamel/&#42;.xml
     */
    private String xmlRests = "classpath:camel-rest/*.xml";

    // Default Properties via camel-main
    // ---------------------------------

    // IMPORTANT: Must include the options from DefaultConfigurationProperties as spring boot apt compiler
    //            needs to grab the documentation from the javadoc on the field.

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
     * Notice the suppress is a best effort as there may still be some logs coming
     * from 3rd party libraries and whatnot, which Camel cannot control.
     * This option is default false.
     */
    private boolean shutdownSuppressLoggingOnTimeout;

    /**
     * Sets whether to force shutdown of all consumers when a timeout occurred and thus
     * not all consumers was shutdown within that period.
     *
     * You should have good reasons to set this option to false as it means that the routes
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
     * Whether to load custom type converters by scanning classpath.
     * This is used for backwards compatibility with Camel 2.x.
     * Its recommended to migrate to use fast type converter loading
     * by setting <tt>@Converter(generateLoader = true)</tt> on your custom
     * type converter classes.
     */
    private boolean loadTypeConverters = true;

    /**
     * Directory to load additional configuration files that contains
     * configuration values that takes precedence over any other configuration.
     * This can be used to refer to files that may have secret configuration that
     * has been mounted on the file system for containers.
     *
     * You must use either file: or classpath: as prefix to load
     * from file system or classpath. Then you can specify a pattern to load
     * from sub directories and a name pattern such as file:/var/app/secret/*.properties
     */
    private String fileConfigurations;

    /**
     * Used for filtering routes routes matching the given pattern, which follows the following rules:
     *
     * - Match by route id
     * - Match by route input endpoint uri
     *
     * The matching is using exact match, by wildcard and regular expression.
     *
     * For example to only include routes which starts with foo in their route id's, use: include=foo&#42;
     * And to exclude routes which starts from JMS endpoints, use: exclude=jms:&#42;
     *
     * Multiple patterns can be separated by comma, for example to exclude both foo and bar routes, use: exclude=foo&#42;,bar&#42;
     *
     * Exclude takes precedence over include.
     */
    private String routeFilterIncludePattern;

    /**
     * Used for filtering routes routes matching the given pattern, which follows the following rules:
     *
     * - Match by route id
     * - Match by route input endpoint uri
     *
     * The matching is using exact match, by wildcard and regular expression.
     *
     * For example to only include routes which starts with foo in their route id's, use: include=foo&#42;
     * And to exclude routes which starts from JMS endpoints, use: exclude=jms:&#42;
     *
     * Multiple patterns can be separated by comma, for example to exclude both foo and bar routes, use: exclude=foo&#42;,bar&#42;
     *
     * Exclude takes precedence over include.
     */
    private String routeFilterExcludePattern;

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
     * Is used to limit the maximum length of the logging Camel message bodies. If the message body
     * is longer than the limit, the log message is clipped. Use -1 to have unlimited length.
     * Use for example 1000 to log at most 1000 characters.
     */
    private int logDebugMaxChars;

    /**
     * Sets whether stream caching is enabled or not.
     *
     * Default is false.
     */
    private boolean streamCachingEnabled;

    /**
     * Sets the stream caching spool (temporary) directory to use for overflow and spooling to disk.
     *
     * If no spool directory has been explicit configured, then a temporary directory
     * is created in the java.io.tmpdir directory.
     */
    private String streamCachingSpoolDirectory;

    /**
     * Sets a stream caching cipher name to use when spooling to disk to write with encryption.
     * By default the data is not encrypted.
     */
    private String streamCachingSpoolCipher;

    /**
     * Stream caching threshold in bytes when overflow to disk is activated.
     * The default threshold is 128kb.
     * Use -1 to disable overflow to disk.
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
     * Sets whether if just any of the org.apache.camel.spi.StreamCachingStrategy.SpoolRule rules
     * returns true then shouldSpoolCache(long) returns true, to allow spooling to disk.
     * If this option is false, then all the org.apache.camel.spi.StreamCachingStrategy.SpoolRule must
     * return true.
     *
     * The default value is false which means that all the rules must return true.
     */
    private boolean streamCachingAnySpoolRules;

    /**
     * Sets the stream caching buffer size to use when allocating in-memory buffers used for in-memory stream caches.
     *
     * The default size is 4096.
     */
    private int streamCachingBufferSize;

    /**
     * Whether to remove stream caching temporary directory when stopping.
     * This option is default true.
     */
    private boolean streamCachingRemoveSpoolDirectoryWhenStopping = true;

    /**
     * Sets whether stream caching statistics is enabled.
     */
    private boolean streamCachingStatisticsEnabled;

    /**
     * Sets whether backlog tracing is enabled or not.
     *
     * Default is false.
     */
    private boolean backlogTracing;

    /**
     * Sets whether tracing is enabled or not.
     *
     * Default is false.
     */
    private boolean tracing;

    /**
     * Tracing pattern to match which node EIPs to trace.
     * For example to match all To EIP nodes, use to*.
     * The pattern matches by node and route id's
     * Multiple patterns can be separated by comma.
     */
    private String tracingPattern;

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
     * Sets whether the object should automatically start when Camel starts.
     * Important: Currently only routes can be disabled, as CamelContext's are always started.
     * Note: When setting auto startup false on CamelContext then that takes precedence
     * and no routes is started. You would need to start CamelContext explicit using
     * the org.apache.camel.CamelContext.start() method, to start the context, and then
     * you would need to start the routes manually using Camelcontext.getRouteController().startRoute(String).
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
     *
     * Data type are automatic turned on if one ore more routes has been explicit configured with input and output types.
     * Otherwise data type is default off.
     */
    private boolean useDataType;

    /**
     * Set whether breadcrumb is enabled.
     * The default value is false.
     */
    private boolean useBreadcrumb;

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
     * To turn on MDC logging
     */
    private boolean useMdcLogging;

    /**
     * Sets the pattern used for determine which custom MDC keys to propagate during message routing when
     * the routing engine continues routing asynchronously for the given message. Setting this pattern to * will
     * propagate all custom keys. Or setting the pattern to foo*,bar* will propagate any keys starting with
     * either foo or bar.
     * Notice that a set of standard Camel MDC keys are always propagated which starts with camel. as key name.
     *
     * The match rules are applied in this order (case insensitive):
     *
     * 1. exact match, returns true
     * 2. wildcard match (pattern ends with a * and the name starts with the pattern), returns true
     * 3. regular expression match, returns true
     * 4. otherwise returns false
     */
    private String mdcLoggingKeysPattern;

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
    private String threadNamePattern;

    /**
     * Sets whether bean introspection uses extended statistics.
     * The default is false.
     */
    private boolean beanIntrospectionExtendedStatistics;

    /**
     * Sets the logging level used by bean introspection, logging activity of its usage.
     * The default is TRACE.
     */
    private LoggingLevel beanIntrospectionLoggingLevel;

    /**
     * Used for inclusive filtering component scanning of RouteBuilder classes with @Component annotation.
     * The exclusive filtering takes precedence over inclusive filtering.
     * The pattern is using Ant-path style pattern.
     *
     * Multiple patterns can be specified separated by comma.
     * For example to include all classes starting with Foo use: &#42;&#42;/Foo*
     * To include all routes form a specific package use: com/mycompany/foo/&#42;
     * To include all routes form a specific package and its sub-packages use double wildcards: com/mycompany/foo/&#42;&#42;
     * And to include all routes from two specific packages use: com/mycompany/foo/&#42;,com/mycompany/stuff/&#42;
     */
    private String javaRoutesIncludePattern;

    /**
     * Used for exclusive filtering component scanning of RouteBuilder classes with @Component annotation.
     * The exclusive filtering takes precedence over inclusive filtering.
     * The pattern is using Ant-path style pattern.
     * Multiple patterns can be specified separated by comma.
     *
     * For example to exclude all classes starting with Bar use: &#42;&#42;/Bar&#42;
     * To exclude all routes form a specific package use: com/mycompany/bar/&#42;
     * To exclude all routes form a specific package and its sub-packages use double wildcards: com/mycompany/bar/&#42;&#42;
     * And to exclude all routes from two specific packages use: com/mycompany/bar/&#42;,com/mycompany/stuff/&#42;
     */
    private String javaRoutesExcludePattern;

    // Getters & setters
    // -----------------

    public boolean isMainRunController() {
        return mainRunController;
    }

    public void setMainRunController(boolean mainRunController) {
        this.mainRunController = mainRunController;
    }

    public boolean isIncludeNonSingletons() {
        return includeNonSingletons;
    }

    public void setIncludeNonSingletons(boolean includeNonSingletons) {
        this.includeNonSingletons = includeNonSingletons;
    }

    public boolean isWarnOnEarlyShutdown() {
        return warnOnEarlyShutdown;
    }

    public void setWarnOnEarlyShutdown(boolean warnOnEarlyShutdown) {
        this.warnOnEarlyShutdown = warnOnEarlyShutdown;
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
}
