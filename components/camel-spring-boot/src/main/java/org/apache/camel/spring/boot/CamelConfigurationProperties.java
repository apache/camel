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
     * Whether to use the main run controller to ensure the Spring-Boot application
     * keeps running until being stopped or the JVM terminated.
     * You typically only need this if you run Spring-Boot standalone.
     * If you run Spring-Boot with spring-boot-starter-web then the web container keeps the JVM running.
     */
    private boolean mainRunController;

    /**
     * Is used to limit the maximum length of the logging Camel message bodies. If the message body
     * is longer than the limit, the log message is clipped. Use a value of 0 or negative to have unlimited length.
     * Use for example 1000 to log at at most 1000 chars.
     */
    private int logDebugMaxChars;

    /**
     * Sets whether stream caching is enabled or not.
     *
     * Default is false.
     */
    private boolean streamCaching;

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
     * Default is true.
     */
    private boolean allowUseOriginalMessage = true;

    /**
     * Sets whether endpoint runtime statistics is enabled (gathers runtime usage of each incoming and outgoing endpoints).
     *
     * The default value is true.
     */
    private boolean endpointRuntimeStatisticsEnabled = true;

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

    // Getters & setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public boolean isMainRunController() {
        return mainRunController;
    }

    public void setMainRunController(boolean mainRunController) {
        this.mainRunController = mainRunController;
    }

    public int getLogDebugMaxChars() {
        return logDebugMaxChars;
    }

    public void setLogDebugMaxChars(int logDebugMaxChars) {
        this.logDebugMaxChars = logDebugMaxChars;
    }

    public boolean isStreamCaching() {
        return streamCaching;
    }

    public void setStreamCaching(boolean streamCaching) {
        this.streamCaching = streamCaching;
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
}
